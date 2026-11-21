package com.techstore.payments.webhook;

import com.techstore.payments.PaymentAttemptStatus;
import com.techstore.payments.PaymentOutcome;
import com.techstore.orders.payments.PaymentIncidentReason;
import com.techstore.orders.payments.PaymentIncidentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializa recepción por pedido (primero cabecera FOR UPDATE) e intento.
 * Unicidades SQL cubren carreras entre eventos de distintos pedidos.
 * No actualiza orders, payment_attempts ni stock_reservations.
 */
@Repository
public class JdbcVerifiedWebhookEventRepository
        implements VerifiedWebhookEventRepository {
    private final JdbcTemplate jdbc;
    private final PaymentIncidentRepository incidents;

    public JdbcVerifiedWebhookEventRepository(
            JdbcTemplate jdbc, PaymentIncidentRepository incidents) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.incidents = Objects.requireNonNull(incidents);
    }

    @Override
    @Transactional
    public WebhookEventReceipt record(
            VerifiedSandboxWebhookEvent event, String digest) {
        Objects.requireNonNull(event);
        if (digest == null || !digest.matches("[A-F0-9]{64}")) {
            throw new InvalidWebhookEventException();
        }

        List<Long> orders = jdbc.query("""
                SELECT order_id FROM payment_attempts WHERE provider_reference=?
                """, (rs, row) -> rs.getLong(1), event.providerReference());
        if (orders.size() != 1 || orders.getFirst() != event.orderId()) {
            throw new WebhookConflictException();
        }
        List<Long> locked = jdbc.query("""
                SELECT id FROM orders WHERE id=? FOR UPDATE
                """, (rs, row) -> rs.getLong(1), event.orderId());
        if (locked.isEmpty()) {
            throw new WebhookConflictException();
        }
        List<Attempt> attempts = jdbc.query("""
                SELECT id, total_crc, status FROM payment_attempts
                WHERE provider_reference=? AND order_id=? FOR UPDATE
                """, (rs, row) -> new Attempt(rs.getLong(1),
                        rs.getBigDecimal(2), PaymentAttemptStatus.valueOf(rs.getString(3))),
                event.providerReference(), event.orderId());
        if (attempts.size() != 1) {
            throw new WebhookConflictException();
        }
        Attempt attempt = attempts.getFirst();
        PaymentAttemptStatus expected = event.outcome() == PaymentOutcome.APPROVED
                ? PaymentAttemptStatus.SIMULATED_APPROVED
                : PaymentAttemptStatus.SIMULATED_DECLINED;
        if (attempt.status() != expected
                || attempt.totalCrc().compareTo(event.totalCrc()) != 0) {
            throw new WebhookConflictException();
        }

        List<EventRow> existing = byEventId(event.eventId());
        if (!existing.isEmpty()) {
            return checkReplay(existing.getFirst(), attempt.id(), event, digest);
        }
        List<EventRow> forAttempt = jdbc.query("""
                SELECT id, provider_event_id, payment_attempt_id,
                    payload_sha256, outcome FROM payment_webhook_events
                WHERE payment_attempt_id=?
                """, JdbcVerifiedWebhookEventRepository::map, attempt.id());
        if (!forAttempt.isEmpty()) {
            throw new WebhookConflictException();
        }

        List<Long> inserted = jdbc.query("""
                INSERT INTO payment_webhook_events(
                    provider, provider_event_id, payment_attempt_id,
                    payload_sha256, outcome
                ) VALUES('SANDBOX', ?, ?, ?, ?)
                ON CONFLICT DO NOTHING RETURNING id
                """, (rs, row) -> rs.getLong(1), event.eventId(), attempt.id(),
                digest, event.outcome().name());
        if (!inserted.isEmpty()) {
            // Mantener incidencia persistente incluso si auto-confirm está OFF.
            if (event.outcome() == PaymentOutcome.APPROVED && isLate(event.orderId())) {
                incidents.record(event.orderId(), PaymentIncidentReason.LATE_APPROVAL,
                        inserted.getFirst());
            }
            return new WebhookEventReceipt(
                    inserted.getFirst(), event.eventId(), attempt.id(), false);
        }
        // Otra petición insertó el mismo evento/ID en paralelo.
        List<EventRow> concurrent = byEventId(event.eventId());
        if (concurrent.isEmpty()) {
            throw new WebhookConflictException();
        }
        return checkReplay(concurrent.getFirst(), attempt.id(), event, digest);
    }

    private boolean isLate(long orderId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT o.status<>'PENDING'
                  OR EXISTS (SELECT 1 FROM stock_reservations r
                    WHERE r.order_id=o.id AND
                        (r.status<>'RESERVED' OR r.expires_at<=CURRENT_TIMESTAMP))
                FROM orders o WHERE o.id=?
                """, Boolean.class, orderId));
    }

    private List<EventRow> byEventId(String eventId) {
        return jdbc.query("""
                SELECT id, provider_event_id, payment_attempt_id,
                    payload_sha256, outcome FROM payment_webhook_events
                WHERE provider='SANDBOX' AND provider_event_id=?
                """, JdbcVerifiedWebhookEventRepository::map, eventId);
    }

    private WebhookEventReceipt checkReplay(
            EventRow previous, long attemptId,
            VerifiedSandboxWebhookEvent event, String digest) {
        if (previous.attemptId() != attemptId
                || !previous.digest().equals(digest)
                || !previous.outcome().equals(event.outcome().name())) {
            throw new WebhookConflictException();
        }
        return new WebhookEventReceipt(
                previous.id(), previous.eventId(), previous.attemptId(), true);
    }

    private static EventRow map(java.sql.ResultSet rs, int row)
            throws java.sql.SQLException {
        return new EventRow(rs.getLong(1), rs.getString(2),
                rs.getLong(3), rs.getString(4).strip(), rs.getString(5));
    }

    private record Attempt(
            long id, BigDecimal totalCrc, PaymentAttemptStatus status) { }
    private record EventRow(
            long id, String eventId, long attemptId, String digest,
            String outcome) { }
}
