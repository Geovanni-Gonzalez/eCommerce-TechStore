package com.techstore.orders.domain;

import java.math.BigDecimal;

/**
 * Nombre, cantidad y precio congelados al crear el pedido.
 * No se vuelven a leer desde products al consultar pedidos históricos.
 */
public record OrderItemSnapshot(
        long productId,
        String name,
        int quantity,
        BigDecimal unitPriceCrc
) {
    public OrderItemSnapshot {
        if (productId < 1 || quantity < 1 || quantity > 99) {
            throw new IllegalArgumentException("Producto o cantidad inválidos");
        }
        if (name == null) {
            throw new IllegalArgumentException("El nombre histórico es obligatorio");
        }
        name = name.strip();
        if (name.isEmpty() || name.length() > 160
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Nombre histórico inválido");
        }
        unitPriceCrc = CrcMoney.require(unitPriceCrc, 12, "unitPriceCrc");
        CrcMoney.require(
                unitPriceCrc.multiply(BigDecimal.valueOf(quantity)),
                14, "lineTotalCrc");
    }

    public BigDecimal lineTotalCrc() {
        return unitPriceCrc.multiply(BigDecimal.valueOf(quantity));
    }
}
