package com.techstore.orders.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** Agregado histórico leído de PostgreSQL, con estado y pago simulado. */
public record Order(
        long id,
        long userId,
        OrderStatus status,
        PaymentMode paymentMode,
        ShippingAddress shippingAddress,
        List<OrderItemSnapshot> items,
        OrderTotals totals,
        OffsetDateTime createdAt
) {
    public Order {
        if (id < 1) {
            throw new IllegalArgumentException("Identificador de pedido inválido");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(paymentMode, "paymentMode");
        Objects.requireNonNull(createdAt, "createdAt");
        // Conserva la validación de subtotales y listas inmutables del borrador.
        OrderDraft checked = new OrderDraft(userId, shippingAddress, items, totals);
        items = checked.items();
    }
}
