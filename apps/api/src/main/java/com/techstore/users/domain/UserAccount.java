package com.techstore.users.domain;

import java.time.OffsetDateTime;

/**
 * Modelo de cuenta almacenada. No devolver directamente como respuesta HTTP:
 * incluye un hash de contraseña que solo debe utilizar autenticación.
 */
public record UserAccount(
        Long id,
        String displayName,
        UserEmail email,
        PasswordHash passwordHash,
        UserRole role,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", role=" + role
                + ", active=" + active + "]";
    }
}
