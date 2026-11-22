package com.techstore.inventory.admin;

public final class InventoryAdjustmentConflict extends RuntimeException {
    public InventoryAdjustmentConflict() {
        super("Saldo insuficiente, límite alcanzado o requestId reutilizado");
    }
}
