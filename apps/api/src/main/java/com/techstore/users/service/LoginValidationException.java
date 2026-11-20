package com.techstore.users.service;

import java.util.Map;

/** Los errores incluyen reglas infringidas, nunca valores de credenciales. */
public class LoginValidationException extends IllegalArgumentException {

    private final Map<String, String> errors;

    public LoginValidationException(Map<String, String> errors) {
        super("Los datos de autenticación son inválidos");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
