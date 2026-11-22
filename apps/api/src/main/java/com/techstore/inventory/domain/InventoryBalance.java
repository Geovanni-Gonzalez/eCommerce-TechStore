package com.techstore.inventory.domain;

/** Stock físico = disponible + reservado, calculado en PostgreSQL. */
public record InventoryBalance(
        long productId, long physicalStock, long reservedStock, long availableStock
) {
    public InventoryBalance {
        if (productId < 1 || physicalStock < 0 || reservedStock < 0
                || availableStock < 0 || physicalStock != availableStock + reservedStock) {
            throw new IllegalArgumentException("Balance de inventario incoherente");
        }
    }
}
