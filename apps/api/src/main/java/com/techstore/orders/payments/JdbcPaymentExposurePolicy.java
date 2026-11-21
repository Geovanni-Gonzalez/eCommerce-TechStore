package com.techstore.orders.payments;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Política conservadora BKL-041, aplicada incluso sin gateway/webhook activado.
 * Los intentos STARTED sin sesión o con sesión CANJEADA pudieron invocar gateway.
 * Solo un STARTED con sesión local sin canjear nunca llegó a autorizar en BKL-037.
 *
 * Orden de locks compartido: orders -> payment_attempts -> checkout_sessions.
 */
@Repository
public class JdbcPaymentExposurePolicy implements PaymentExposurePolicy {
    private final JdbcTemplate jdbc;

    public JdbcPaymentExposurePolicy(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public PaymentIncidentReason blockingReason(long orderId) {
        if (orderId < 1) {
            throw new IllegalArgumentException("Pedido inválido");
        }
        // Caller holds orders FOR UPDATE, so claims/cancels/canjeos are serialized.
        List<AttemptState> attempts = jdbc.query("""
                SELECT a.id,a.status,
                    (SELECT e.outcome FROM payment_webhook_events e
                     WHERE e.payment_attempt_id=a.id) AS event_outcome,
                    (SELECT e.reconciliation_status FROM payment_webhook_events e
                     WHERE e.payment_attempt_id=a.id) AS reconciliation
                FROM payment_attempts a WHERE a.order_id=?
                ORDER BY a.id FOR UPDATE
                """, (rs,row) -> new AttemptState(
                    rs.getLong("id"), rs.getString("status"),
                    rs.getString("event_outcome"), rs.getString("reconciliation")),
                orderId);
        PaymentIncidentReason selected = null;
        for (AttemptState a : attempts) {
            if ("SIMULATED_APPROVED".equals(a.status())
                    || "APPROVED".equals(a.eventOutcome())) {
                return PaymentIncidentReason.AWAITING_VERIFIED_APPROVAL;
            }
            if ("NEEDS_REVIEW".equals(a.reconciliation())) {
                selected = PaymentIncidentReason.EVIDENCE_MISMATCH;
            }
            if ("UNKNOWN".equals(a.status())) {
                selected = PaymentIncidentReason.AMBIGUOUS_AUTHORIZATION;
            }
            if ("STARTED".equals(a.status())) {
                // Lock the checkout session row. A missing/redeemed session means
                // a gateway invocation may already have happened: hold stock.
                List<OffsetDateTime> redemption = jdbc.query("""
                        SELECT redeemed_at FROM sandbox_checkout_sessions
                        WHERE payment_attempt_id=? FOR UPDATE
                        """, (rs,row) -> rs.getObject("redeemed_at",
                            OffsetDateTime.class), a.id());
                if (redemption.isEmpty() || redemption.getFirst() != null) {
                    selected = PaymentIncidentReason.AMBIGUOUS_AUTHORIZATION;
                }
            }
        }
        return selected;
    }

    private record AttemptState(long id, String status, String eventOutcome,
            String reconciliation) { }
}
