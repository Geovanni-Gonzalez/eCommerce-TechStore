package com.techstore.users.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error seguro de entrada: solo almacena los nombres de los campos
 * y sus reglas infringidas; no almacena valores ni contraseñas.
 */
public class RegistrationValidationException extends IllegalArgumentException {

    private final Map<String, String> errors;

    public RegistrationValidationException(Map<String, String> errors) {
        super("Los datos del registro no son válidos");
        this.errors = Map.copyOf(new LinkedHashMap<>(errors));
    }

    public Map<String, String> errors() {
        return errors;
    }
}
