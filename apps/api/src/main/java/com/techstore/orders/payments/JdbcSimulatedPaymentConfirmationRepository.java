package com.techstore.orders.payments;

import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.history.OrderChangeActor;
import com.techstore.orders.history.OrderChangeReason;
import com.techstore.orders.history.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todas las escrituras (order, reservas, historial y evento) ocurren en
 * una sola transacción. Se bloquea orders ANTES que intento/reservas/evento.
 * Este adaptador SOLO permite confirmación simulada explícitamente habilitada.
 */
@Repository
public class JdbcSimulatedPaymentConfirmationRepository {
    private final JdbcTemplate jdbc;
    private final OrderStatusHistoryRepository history;
    private final PaymentIncidentRepository incidents;

    public JdbcSimulatedPaymentConfirmationRepository(
            JdbcTemplate jdbc, OrderStatusHistoryRepository history,
            PaymentIncidentRepository incidents) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.history = Objects.requireNonNull(history);
        this.incidents = Objects.requireNonNull(incidents);
    }

    @Transactional
    public PaymentReconciliationResult reconcile(long eventId) {
        if (eventId < 1) {
            throw new PaymentReconciliationNotFoundException();
        }
        List<Long> orderIds = jdbc.query("""
                SELECT a.order_id
                FROM payment_webhook_events e
                JOIN payment_attempts a ON a.id=e.payment_attempt_id
                WHERE e.id=?
                """, (rs, row) -> rs.getLong(1), eventId);
        if (orderIds.size() != 1) {
            throw new PaymentReconciliationNotFoundException();
        }
        long orderId = orderIds.getFirst();

        // Cancela/expira también bloquea primero orders; sin deadlock inverso.
        List<Long> locks = jdbc.query("""
                SELECT id FROM orders WHERE id=? FOR UPDATE
                """, (rs, row) -> rs.getLong(1), orderId);
        if (locks.size() != 1) {
            throw new PaymentReconciliationNotFoundException();
        }
        List<Evidence> evidence = jdbc.query("""
                SELECT e.id, e.provider, e.outcome, e.reconciliation_status,
                    a.id AS attempt_id, a.status AS attempt_status,
                    a.provider_reference, a.total_crc AS attempt_total,
                    o.status AS order_status, o.payment_mode,
                    o.total_crc AS order_total
                FROM payment_webhook_events e
                JOIN payment_attempts a ON a.id=e.payment_attempt_id
                JOIN orders o ON o.id=a.order_id
                WHERE e.id=? AND o.id=?
                FOR UPDATE OF e, a
                """, (rs, row) -> new Evidence(
                    rs.getString("provider"), rs.getString("outcome"),
                    PaymentReconciliationStatus.valueOf(
                        rs.getString("reconciliation_status")),
                    rs.getString("attempt_status"), rs.getString("provider_reference"),
                    rs.getBigDecimal("attempt_total"), rs.getString("order_status"),
                    rs.getString("payment_mode"), rs.getBigDecimal("order_total")),
                eventId, orderId);
        if (evidence.size() != 1) {
            throw new PaymentReconciliationNotFoundException();
        }
        Evidence proof = evidence.getFirst();
        if (proof.reconciliation() != PaymentReconciliationStatus.RECEIVED) {
            return new PaymentReconciliationResult(
                    eventId, orderId, proof.reconciliation());
        }
        if (!"SANDBOX".equals(proof.provider())
                || !"SIMULATED".equals(proof.paymentMode())
                || proof.reference() == null
                || !proof.reference().matches("SBX-[A-Z0-9-]{8,60}")
                || proof.attemptTotal().compareTo(proof.orderTotal()) != 0) {
            return mark(eventId, orderId, PaymentReconciliationStatus.NEEDS_REVIEW);
        }
        if ("DECLINED".equals(proof.outcome())) {
            // El rechazo debe coincidir con el intento, no cancelar el pedido.
            return mark(eventId, orderId,
                    "SIMULATED_DECLINED".equals(proof.attemptStatus())
                        ? PaymentReconciliationStatus.DECLINED
                        : PaymentReconciliationStatus.NEEDS_REVIEW);
        }
        if (!"APPROVED".equals(proof.outcome())
                || !"SIMULATED_APPROVED".equals(proof.attemptStatus())) {
            return mark(eventId, orderId, PaymentReconciliationStatus.NEEDS_REVIEW);
        }

        // CANCELLED/CONFIRMED o reservas tardías nunca producen un segundo consumo.
        if (!"PENDING".equals(proof.orderStatus())) {
            return mark(eventId, orderId, PaymentReconciliationStatus.NEEDS_REVIEW);
        }

        List<Line> items = jdbc.query("""
                SELECT product_id, quantity FROM order_items
                WHERE order_id=? ORDER BY product_id
                """, (rs, row) -> new Line(rs.getLong(1), rs.getInt(2)), orderId);
        List<Reservation> reserved = jdbc.query("""
                SELECT product_id, quantity, status,
                    expires_at > CURRENT_TIMESTAMP AS still_valid
                FROM stock_reservations WHERE order_id=?
                ORDER BY product_id FOR UPDATE
                """, (rs, row) -> new Reservation(rs.getLong("product_id"),
                    rs.getInt("quantity"), rs.getString("status"),
                    rs.getBoolean("still_valid")), orderId);
        if (items.isEmpty() || reserved.size() != items.size()) {
            return mark(eventId, orderId, PaymentReconciliationStatus.NEEDS_REVIEW);
        }
        for (int n = 0; n < items.size(); n++) {
            Line item = items.get(n);
            Reservation reservation = reserved.get(n);
            if (item.productId() != reservation.productId()
                    || item.quantity() != reservation.quantity()
                    || !"RESERVED".equals(reservation.status())
                    || !reservation.stillValid()) {
                return mark(eventId, orderId, PaymentReconciliationStatus.NEEDS_REVIEW);
            }
        }

        // NO descontar products.stock de nuevo: BKL-033 ya lo reservó.
        int consumed = jdbc.update("""
                UPDATE stock_reservations
                SET status='CONSUMED', updated_at=CURRENT_TIMESTAMP
                WHERE order_id=? AND status='RESERVED'
                  AND expires_at > CURRENT_TIMESTAMP
                """, orderId);
        if (consumed != reserved.size()) {
            // Rollback de TODAS las escrituras; evento sigue RECEIVED para retry.
            throw new IllegalStateException("Reserva cambió durante conciliación");
        }
        int updated = jdbc.update("""
                UPDATE orders SET status='CONFIRMED', updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='PENDING' AND payment_mode='SIMULATED'
                """, orderId);
        if (updated != 1) {
            throw new IllegalStateException("Estado de pedido cambió durante conciliación");
        }
        history.recordTransition(orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED,
                OrderChangeActor.SYSTEM, null, OrderChangeReason.SANDBOX_VERIFIED);
        return mark(eventId, orderId,
                PaymentReconciliationStatus.CONFIRMED_SIMULATED);
    }

    private PaymentReconciliationResult mark(long eventId, long orderId,
            PaymentReconciliationStatus result) {
        int changed = jdbc.update("""
                UPDATE payment_webhook_events
                SET reconciliation_status=?, reconciled_at=CURRENT_TIMESTAMP
                WHERE id=? AND reconciliation_status='RECEIVED'
                """, result.name(), eventId);
        if (changed != 1) {
            throw new IllegalStateException("Estado de evento cambió en conciliación");
        }
        if (result == PaymentReconciliationStatus.NEEDS_REVIEW) {
            Boolean late = jdbc.queryForObject("""
                    SELECT o.status<>'PENDING'
                      OR EXISTS (SELECT 1 FROM stock_reservations r
                          WHERE r.order_id=o.id AND
                            (r.status<>'RESERVED' OR r.expires_at<=CURRENT_TIMESTAMP))
                    FROM orders o WHERE o.id=?
                    """, Boolean.class, orderId);
            incidents.record(orderId, Boolean.TRUE.equals(late)
                    ? PaymentIncidentReason.LATE_APPROVAL
                    : PaymentIncidentReason.EVIDENCE_MISMATCH, eventId);
        }
        return new PaymentReconciliationResult(eventId, orderId, result);
    }

    private record Evidence(
            String provider, String outcome, PaymentReconciliationStatus reconciliation,
            String attemptStatus, String reference, BigDecimal attemptTotal,
            String orderStatus, String paymentMode, BigDecimal orderTotal) { }
    private record Line(long productId, int quantity) { }
    private record Reservation(
            long productId, int quantity, String status, boolean stillValid) { }
}
