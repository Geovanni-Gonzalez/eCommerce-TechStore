package com.techstore.users.dto;

import com.techstore.users.service.RegistrationValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Datos de entrada exclusivos del registro. Nunca se debe registrar su valor
 * completo en logs porque contiene una contraseña en texto plano.
 */
public record RegisterRequest(String displayName, String email, String password) {

    private static final Set<String> ALLOWED =
            Set.of("displayName", "email", "password");

    public static RegisterRequest fromJsonMap(Map<String, Object> input) {
        if (input == null) {
            throw new RegistrationValidationException(
                    Map.of("body", "El cuerpo JSON es obligatorio"));
        }

        Map<String, String> errors = new LinkedHashMap<>();
        for (String field : input.keySet()) {
            if (!ALLOWED.contains(field)) {
                errors.put("body", "La solicitud contiene campos no permitidos");
                break;
            }
        }
        String displayName = stringField(input, "displayName", errors);
        String email = stringField(input, "email", errors);
        String password = stringField(input, "password", errors);
        if (!errors.isEmpty()) {
            throw new RegistrationValidationException(errors);
        }
        return new RegisterRequest(displayName, email, password);
    }

    private static String stringField(
            Map<String, Object> input, String field, Map<String, String> errors) {
        Object value = input.get(field);
        if (!(value instanceof String text)) {
            errors.put(field, "Debe ser un texto");
            return null;
        }
        return text;
    }

    @Override
    public String toString() {
        return "RegisterRequest[REDACTED]";
    }
}
