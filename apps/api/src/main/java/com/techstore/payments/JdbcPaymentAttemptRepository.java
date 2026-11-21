package com.techstore.payments;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * BKL-036: claim transaccional serializado con FOR UPDATE sobre orders,
 * compatible con orden de bloqueos de cancelación/expiración BKL-027.
 * La llamada al proveedor NUNCA ocurre en esta transacción.
 */
@Repository
public class JdbcPaymentAttemptRepository implements PaymentAttemptRepository {
    private final JdbcTemplate jdbc;

    public JdbcPaymentAttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional
    public PaymentAttemptClaim claim(long userId, long orderId, String keyHash) {
        if (userId < 1 || orderId < 1 || keyHash == null
                || !keyHash.matches("[A-F0-9]{64}")) {
            throw new IllegalArgumentException("Datos de intento inválidos");
        }
        List<OrderInfo> headers = jdbc.query("""
                SELECT status, total_crc FROM orders
                WHERE id=? AND user_id=? FOR UPDATE
                """, (rs, row) -> new OrderInfo(rs.getString(1),
                        rs.getBigDecimal(2)), orderId, userId);
        if (headers.isEmpty()) {
            throw new PaymentAttemptNotFoundException();
        }
        OrderInfo order = headers.getFirst();

        List<PaymentAttemptSnapshot> existing = jdbc.query("""
                SELECT id, order_id, total_crc, status, provider_reference,
                    created_at, updated_at FROM payment_attempts
                WHERE user_id=? AND idempotency_key_hash=?
                """, JdbcPaymentAttemptRepository::map, userId, keyHash);
        if (!existing.isEmpty()) {
            PaymentAttemptSnapshot attempt = existing.getFirst();
            if (attempt.orderId() != orderId
                    || attempt.totalCrc().compareTo(order.totalCrc()) != 0) {
                throw new PaymentAttemptConflictException();
            }
            return new PaymentAttemptClaim(attempt, false);
        }

        // No se cobra una reserva cancelada, consumida o que ya venció.
        if (!"PENDING".equals(order.status()) || order.totalCrc().signum() <= 0) {
            throw new PaymentAttemptConflictException();
        }
        Boolean validReservations = jdbc.queryForObject("""
                SELECT COALESCE(BOOL_AND(
                    status = 'RESERVED' AND expires_at > CURRENT_TIMESTAMP), FALSE)
                FROM stock_reservations WHERE order_id=?
                """, Boolean.class, orderId);
        if (!Boolean.TRUE.equals(validReservations)) {
            throw new PaymentAttemptConflictException();
        }
        Boolean hasBlockingAttempt = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM payment_attempts
                    WHERE order_id=? AND status IN (
                        'STARTED','SIMULATED_APPROVED','UNKNOWN'))
                """, Boolean.class, orderId);
        if (Boolean.TRUE.equals(hasBlockingAttempt)) {
            throw new PaymentAttemptConflictException();
        }
        Long attemptId = jdbc.queryForObject("""
                INSERT INTO payment_attempts(
                    order_id, user_id, idempotency_key_hash, total_crc, status)
                VALUES (?, ?, ?, ?, 'STARTED') RETURNING id
                """, Long.class, orderId, userId, keyHash, order.totalCrc());
        return new PaymentAttemptClaim(read(Objects.requireNonNull(attemptId)), true);
    }

    @Override
    @Transactional
    public PaymentAttemptSnapshot complete(long attemptId, PaymentAttemptStatus status,
            String reference) {
        if (attemptId < 1 || status == null || status == PaymentAttemptStatus.STARTED) {
            throw new IllegalArgumentException("Resultado de intento inválido");
        }
        boolean hasReference = status == PaymentAttemptStatus.SIMULATED_APPROVED
                || status == PaymentAttemptStatus.SIMULATED_DECLINED;
        if (hasReference != (reference != null)) {
            throw new IllegalArgumentException("Referencia de proveedor incompatible");
        }
        int changed = jdbc.update("""
                UPDATE payment_attempts
                SET status=?, provider_reference=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='STARTED'
                """, status.name(), reference, attemptId);
        if (changed != 1) {
            throw new IllegalStateException("El intento ya fue completado");
        }
        return read(attemptId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentAttemptSnapshot findForOwner(long userId, long orderId,
            long attemptId) {
        if (userId < 1 || orderId < 1 || attemptId < 1) {
            throw new PaymentAttemptNotFoundException();
        }
        List<PaymentAttemptSnapshot> matches = jdbc.query("""
                SELECT id, order_id, total_crc, status, provider_reference,
                    created_at, updated_at FROM payment_attempts
                WHERE id=? AND order_id=? AND user_id=?
                """, JdbcPaymentAttemptRepository::map, attemptId, orderId, userId);
        return matches.stream().findFirst()
                .orElseThrow(PaymentAttemptNotFoundException::new);
    }

    private PaymentAttemptSnapshot read(long id) {
        return jdbc.queryForObject("""
                SELECT id, order_id, total_crc, status, provider_reference,
                    created_at, updated_at FROM payment_attempts WHERE id=?
                """, JdbcPaymentAttemptRepository::map, id);
    }

    private static PaymentAttemptSnapshot map(ResultSet rs, int row)
            throws SQLException {
        return new PaymentAttemptSnapshot(
                rs.getLong("id"), rs.getLong("order_id"),
                rs.getBigDecimal("total_crc"),
                PaymentAttemptStatus.valueOf(rs.getString("status")),
                rs.getString("provider_reference"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private record OrderInfo(String status, BigDecimal totalCrc) { }
}
