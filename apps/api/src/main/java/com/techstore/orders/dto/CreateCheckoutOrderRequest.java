package com.techstore.orders.dto;

import com.techstore.orders.domain.ShippingAddress;
import java.util.Map;
import java.util.Set;

/**
 * Body estricto. Se prohíbe enviar líneas, totales, stock o userId.
 * La solicitud solo entrega dirección para crear el pedido del carrito propio.
 */
public record CreateCheckoutOrderRequest(ShippingAddress shippingAddress) {
    private static final Set<String> ADDRESS_FIELDS = Set.of(
            "province", "canton", "district", "details", "recipientName", "phone");

    public static CreateCheckoutOrderRequest parse(Map<String, Object> body) {
        if (body == null || !body.keySet().equals(Set.of("shippingAddress"))
                || !(body.get("shippingAddress") instanceof Map<?, ?> address)
                || !address.keySet().equals(ADDRESS_FIELDS)) {
            throw new InvalidCheckoutRequestException();
        }
        try {
            return new CreateCheckoutOrderRequest(new ShippingAddress(
                    text(address, "province"), text(address, "canton"),
                    text(address, "district"), text(address, "details"),
                    text(address, "recipientName"), text(address, "phone")));
        } catch (IllegalArgumentException ex) {
            throw new InvalidCheckoutRequestException();
        }
    }

    private static String text(Map<?, ?> input, String field) {
        Object value = input.get(field);
        if (!(value instanceof String string)) {
            throw new InvalidCheckoutRequestException();
        }
        return string;
    }
}
