package com.techstore.payments.sessions;

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
 * Lock order -> attempt -> session para mantener orden común con BKL-036/038.
 * issue participa en la misma transacción que el claim STARTED del intento.
 */
@Repository
public final class JdbcSandboxSessionRepository implements SandboxSessionRepository {
    private final JdbcTemplate jdbc;

    public JdbcSandboxSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional
    public SandboxSessionSnapshot issue(long orderId, long attemptId, String hash) {
        validateHash(hash);
        if (orderId < 1 || attemptId < 1) {
            throw new SandboxSessionConflictException();
        }
        List<Long> locked = jdbc.query("""
                SELECT id FROM orders WHERE id=? FOR UPDATE
                """, (rs, n) -> rs.getLong(1), orderId);
        if (locked.isEmpty()) {
            throw new SandboxSessionConflictException();
        }
        List<AttemptState> attempts = jdbc.query("""
                SELECT a.id, a.total_crc, a.status, o.status AS order_status
                FROM payment_attempts a JOIN orders o ON o.id=a.order_id
                WHERE a.id=? AND a.order_id=? FOR UPDATE OF a
                """, (rs, n) -> new AttemptState(
                    rs.getLong("id"), rs.getBigDecimal("total_crc"),
                    rs.getString("status"), rs.getString("order_status")),
                attemptId, orderId);
        if (attempts.size() != 1 ||
                !"STARTED".equals(attempts.getFirst().status())
                || !"PENDING".equals(attempts.getFirst().orderStatus())) {
            throw new SandboxSessionConflictException();
        }
        // La sesión nunca sobrevive a la reserva más próxima ni excede 10 min.
        List<OffsetDateTime> deadlines = jdbc.query("""
                SELECT MIN(expires_at) AS deadline
                FROM stock_reservations WHERE order_id=?
                HAVING COUNT(*) > 0
                   AND BOOL_AND(status='RESERVED' AND expires_at > CURRENT_TIMESTAMP)
                """, (rs, n) -> rs.getObject("deadline", OffsetDateTime.class), orderId);
        if (deadlines.isEmpty()) {
            throw new SandboxSessionConflictException();
        }

        List<Long> sessions = jdbc.query("""
                SELECT id FROM sandbox_checkout_sessions
                WHERE payment_attempt_id=? FOR UPDATE
                """, (rs, n) -> rs.getLong(1), attemptId);
        // Reintentar misma clave rota el enlace anterior: un intento, no dos pagos.
        if (sessions.isEmpty()) {
            jdbc.update("""
                    INSERT INTO sandbox_checkout_sessions(
                        payment_attempt_id, token_hash, expires_at)
                    VALUES (?, ?, LEAST(CURRENT_TIMESTAMP + INTERVAL '10 minutes', ?))
                    """, attemptId, hash, deadlines.getFirst());
        } else {
            int changed = jdbc.update("""
                    UPDATE sandbox_checkout_sessions SET token_hash=?,
                        expires_at=LEAST(CURRENT_TIMESTAMP + INTERVAL '10 minutes', ?),
                        updated_at=CURRENT_TIMESTAMP
                    WHERE payment_attempt_id=? AND redeemed_at IS NULL
                    """, hash, deadlines.getFirst(), attemptId);
            if (changed != 1) {
                throw new SandboxSessionConflictException();
            }
        }
        return jdbc.queryForObject("""
                SELECT a.id AS attempt_id, a.order_id, a.total_crc,
                       s.expires_at
                FROM sandbox_checkout_sessions s
                JOIN payment_attempts a ON a.id=s.payment_attempt_id
                WHERE s.payment_attempt_id=?
                """, JdbcSandboxSessionRepository::map, attemptId);
    }

    @Override
    @Transactional(readOnly = true)
    public SandboxSessionSnapshot preview(String hash) {
        validateHash(hash);
        List<SessionRow> sessions = load(hash, false);
        if (sessions.isEmpty() || !valid(sessions.getFirst())) {
            throw new SandboxSessionUnavailableException();
        }
        return sessions.getFirst().snapshot();
    }

    @Override
    @Transactional
    public SandboxSessionSnapshot redeem(String hash) {
        validateHash(hash);
        List<Long> orders = jdbc.query("""
                SELECT a.order_id FROM sandbox_checkout_sessions s
                JOIN payment_attempts a ON a.id=s.payment_attempt_id
                WHERE s.token_hash=?
                """, (rs, n) -> rs.getLong(1), hash);
        if (orders.isEmpty()) {
            throw new SandboxSessionUnavailableException();
        }
        // Serializa con cancelaciones, reemisiones y otros canjes.
        jdbc.query("""
                SELECT id FROM orders WHERE id=? FOR UPDATE
                """, (rs, n) -> rs.getLong(1), orders.getFirst());
        List<SessionRow> sessions = load(hash, true);
        if (sessions.isEmpty() || !valid(sessions.getFirst())) {
            throw new SandboxSessionUnavailableException();
        }
        SessionRow selected = sessions.getFirst();
        int changed = jdbc.update("""
                UPDATE sandbox_checkout_sessions
                SET redeemed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND redeemed_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                """, selected.id());
        if (changed != 1) {
            throw new SandboxSessionUnavailableException();
        }
        return selected.snapshot();
    }

    private List<SessionRow> load(String hash, boolean lock) {
        String sql = """
                SELECT s.id, a.id AS attempt_id, a.order_id, a.total_crc,
                       a.status AS attempt_status, o.status AS order_status,
                       s.expires_at, s.redeemed_at,
                       (s.expires_at > CURRENT_TIMESTAMP) AS not_expired,
                       COALESCE((SELECT BOOL_AND(
                           r.status='RESERVED' AND r.expires_at > CURRENT_TIMESTAMP)
                           FROM stock_reservations r WHERE r.order_id=a.order_id),
                           FALSE) AS valid_reservations
                FROM sandbox_checkout_sessions s
                JOIN payment_attempts a ON a.id=s.payment_attempt_id
                JOIN orders o ON o.id=a.order_id
                WHERE s.token_hash=?
                """ + (lock ? " FOR UPDATE OF s, a" : "");
        return jdbc.query(sql, JdbcSandboxSessionRepository::mapSession, hash);
    }

    private static boolean valid(SessionRow s) {
        return s.redeemedAt() == null && s.notExpired()
                && s.validReservations()
                && "PENDING".equals(s.orderStatus())
                && "STARTED".equals(s.attemptStatus());
    }

    private static SandboxSessionSnapshot map(ResultSet rs, int row)
            throws SQLException {
        return new SandboxSessionSnapshot(
                rs.getLong("attempt_id"), rs.getLong("order_id"),
                rs.getBigDecimal("total_crc"), "CRC",
                rs.getObject("expires_at", OffsetDateTime.class));
    }

    private static SessionRow mapSession(ResultSet rs, int row)
            throws SQLException {
        return new SessionRow(rs.getLong("id"),
                new SandboxSessionSnapshot(rs.getLong("attempt_id"),
                    rs.getLong("order_id"), rs.getBigDecimal("total_crc"),
                    "CRC", rs.getObject("expires_at", OffsetDateTime.class)),
                rs.getString("attempt_status"), rs.getString("order_status"),
                rs.getObject("redeemed_at", OffsetDateTime.class),
                rs.getBoolean("not_expired"),
                rs.getBoolean("valid_reservations"));
    }

    private static void validateHash(String hash) {
        if (hash == null || !hash.matches("[A-F0-9]{64}")) {
            throw new SandboxSessionUnavailableException();
        }
    }

    private record AttemptState(long id, BigDecimal amount,
            String status, String orderStatus) { }
    private record SessionRow(long id, SandboxSessionSnapshot snapshot,
            String attemptStatus, String orderStatus,
            OffsetDateTime redeemedAt, boolean notExpired,
            boolean validReservations) { }
}
