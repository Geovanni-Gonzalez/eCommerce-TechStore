package com.techstore.users.dto;

import com.techstore.users.domain.UserAccount;
import com.techstore.users.domain.UserRole;
import java.util.Objects;

/**
 * Contrato público User de OpenAPI: nunca incluye passwordHash ni active.
 * CLIENT es el nombre público del rol interno CLIENTE.
 */
public record RegisteredUserResponse(
        Long id,
        String displayName,
        String email,
        String role
) {
    public static RegisteredUserResponse from(UserAccount user) {
        Objects.requireNonNull(user, "user");
        String publicRole = switch (user.role()) {
            case CLIENTE -> "CLIENT";
            case ADMIN -> "ADMIN";
        };
        return new RegisteredUserResponse(
                user.id(), user.displayName(), user.email().value(), publicRole);
    }
}
