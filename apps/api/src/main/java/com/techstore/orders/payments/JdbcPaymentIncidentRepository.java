package com.techstore.orders.payments;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Insertar mientras se posee el lock de orders. Conflict DO NOTHING mantiene
 * la incidencia original y evita duplicarla ante el cron o replay del webhook.
 */
@Repository
public class JdbcPaymentIncidentRepository implements PaymentIncidentRepository {
    private final JdbcTemplate jdbc;

    public JdbcPaymentIncidentRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void record(long orderId, PaymentIncidentReason reason, Long eventId) {
        if (orderId < 1 || reason == null || (eventId != null && eventId < 1)) {
            throw new IllegalArgumentException("Incidencia de pago inválida");
        }
        jdbc.update("""
                INSERT INTO payment_reconciliation_incidents(
                    order_id,reason,webhook_event_id)
                VALUES (?,?,?) ON CONFLICT (order_id,reason) DO NOTHING
                """, orderId, reason.name(), eventId);
    }

    @Override
    public List<PaymentIncidentView> findForOrder(long orderId) {
        if (orderId < 1) {
            throw new PaymentReconciliationNotFoundException();
        }
        return jdbc.query("""
                SELECT i.id,i.order_id,i.reason,i.webhook_event_id,i.detected_at
                FROM payment_reconciliation_incidents i
                WHERE i.order_id=?
                ORDER BY i.id
                """, (rs,row) -> new PaymentIncidentView(
                    rs.getLong("id"), rs.getLong("order_id"),
                    PaymentIncidentReason.valueOf(rs.getString("reason")),
                    rs.getObject("webhook_event_id",Long.class),
                    rs.getObject("detected_at",java.time.OffsetDateTime.class)),
                orderId);
    }
}
