package com.techstore.users.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Representa SOLO un hash BCrypt, nunca una contraseña en texto plano.
 * No exponer este valor a un controlador ni a un DTO público.
 */
public record PasswordHash(String value) {

    private static final Pattern BCRYPT =
            Pattern.compile("^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$");

    public PasswordHash {
        Objects.requireNonNull(value, "passwordHash");
        if (!BCRYPT.matcher(value).matches()) {
            throw new IllegalArgumentException("Se requiere un hash BCrypt válido");
        }
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }
}
