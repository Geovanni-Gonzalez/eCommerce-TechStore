package com.techstore.users.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Revocación efectiva al actualizar la misma fila que consulta el filtro.
 * La vigencia se evalúa en PostgreSQL, usando su reloj.
 */
@Repository
public class JdbcSessionTokenRepository implements SessionTokenRepository {

    private final JdbcTemplate jdbc;

    public JdbcSessionTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void create(long userId, String tokenHash, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO auth_sessions (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, userId, tokenHash, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    @Override
    public Optional<Long> findActiveUserId(String tokenHash) {
        List<Long> ids = jdbc.query("""
                SELECT user_id FROM auth_sessions
                WHERE token_hash = ? AND revoked_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> rs.getLong("user_id"), tokenHash);
        return ids.stream().findFirst();
    }

    @Override
    public void revoke(String tokenHash) {
        jdbc.update("""
                UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP
                WHERE token_hash = ? AND revoked_at IS NULL
                """, tokenHash);
    }
}
