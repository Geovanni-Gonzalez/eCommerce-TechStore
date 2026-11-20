package com.techstore.orders.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Datos VALIDADOS para persistir un futuro pedido. Deben producirse por el
 * checkout confiable, no deserializarse de JSON arbitrario. No bloquea stock.
 */
public record OrderDraft(
        long userId,
        ShippingAddress shippingAddress,
        List<OrderItemSnapshot> items,
        OrderTotals totals
) {
    public OrderDraft {
        if (userId < 1) {
            throw new IllegalArgumentException("El usuario debe ser válido");
        }
        Objects.requireNonNull(shippingAddress, "shippingAddress");
        Objects.requireNonNull(totals, "totals");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("El pedido requiere líneas");
        }
        Set<Long> products = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (OrderItemSnapshot item : items) {
            if (!products.add(item.productId())) {
                throw new IllegalArgumentException(
                        "No se puede repetir un producto en el mismo pedido");
            }
            subtotal = subtotal.add(item.lineTotalCrc());
        }
        if (totals.subtotalCrc().compareTo(subtotal) != 0) {
            throw new IllegalArgumentException(
                    "El subtotal no coincide con las líneas históricas");
        }
    }
}
