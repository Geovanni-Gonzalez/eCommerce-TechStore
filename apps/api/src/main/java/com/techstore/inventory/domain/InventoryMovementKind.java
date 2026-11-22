package com.techstore.inventory.domain;

/** Movimientos BKL-025, sin información personal de pedidos o clientes. */
public enum InventoryMovementKind {
    OPENING,
    RESERVED,
    RELEASED,
    CONSUMED,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT
}
