package com.techstore.users.repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Contrato de persistencia de sesiones opacas (DIP).
 * El token en claro nunca cruza esta interfaz, solo su hash SHA-256.
 */
public interface SessionTokenRepository {

    void create(long userId, String tokenHash, Instant expiresAt);

    Optional<Long> findActiveUserId(String tokenHash);

    void revoke(String tokenHash);
}
