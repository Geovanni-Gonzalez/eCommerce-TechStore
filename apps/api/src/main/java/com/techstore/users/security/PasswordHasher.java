package com.techstore.users.security;

import com.techstore.users.domain.PasswordHash;

/**
 * Contrato de hashing para que los casos de uso no dependan de BCrypt.
 */
public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash storedHash);
}
