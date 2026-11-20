package com.techstore.users.dto;

import com.techstore.users.service.LoginValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** El password existe exclusivamente en memoria durante la autenticación. */
public record LoginRequest(String email, String password) {

    public static LoginRequest fromJsonMap(Map<String, Object> input) {
        if (input == null) {
            throw new LoginValidationException(Map.of("body", "Se requiere un objeto JSON"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (!Set.of("email", "password").containsAll(input.keySet())) {
            errors.put("body", "La solicitud contiene campos no permitidos");
        }
        String email = stringValue(input, "email", errors);
        String password = stringValue(input, "password", errors);
        if (!errors.isEmpty()) {
            throw new LoginValidationException(errors);
        }
        return new LoginRequest(email, password);
    }

    private static String stringValue(
            Map<String, Object> input, String name, Map<String, String> errors) {
        if (input.get(name) instanceof String text) {
            return text;
        }
        errors.put(name, "Debe ser texto");
        return null;
    }

    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
