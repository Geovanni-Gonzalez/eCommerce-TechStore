package com.techstore.cart.dto;

import com.techstore.cart.service.CartValidationException;
import java.util.Map;
import java.util.Set;

/**
 * Parseo estricto: no se aceptan precios, userId, role, stock u otros campos
 * en el cuerpo. El usuario SIEMPRE procede del principal autenticado.
 */
public record CartItemRequest(long productId, int quantity) {

    public static CartItemRequest create(Map<String, Object> input) {
        requireFields(input, Set.of("productId", "quantity"));
        return new CartItemRequest(positiveLong(input.get("productId"), "productId"),
                positiveInt(input.get("quantity"), "quantity"));
    }

    public static int updateQuantity(Map<String, Object> input) {
        requireFields(input, Set.of("quantity"));
        return positiveInt(input.get("quantity"), "quantity");
    }

    private static void requireFields(Map<String, Object> input, Set<String> allowed) {
        if (input == null || !input.keySet().equals(allowed)) {
            throw new CartValidationException("Los campos de la solicitud no son válidos");
        }
    }

    private static long positiveLong(Object value, String field) {
        if (value instanceof Integer n && n > 0) return n.longValue();
        if (value instanceof Long n && n > 0) return n;
        throw new CartValidationException(field + " debe ser un entero positivo");
    }

    private static int positiveInt(Object value, String field) {
        if (value instanceof Integer n && n > 0 && n <= 99) return n;
        if (value instanceof Long n && n > 0 && n <= 99) return n.intValue();
        throw new CartValidationException(field + " debe estar entre 1 y 99");
    }
}
