package com.techstore.users.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Valor de dominio con normalización conservadora y formato básico.
 * BKL-015 incorporará las demás validaciones del flujo de registro.
 */
public record UserEmail(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public UserEmail {
        Objects.requireNonNull(value, "email");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("El correo electrónico no es válido");
        }
    }
}
