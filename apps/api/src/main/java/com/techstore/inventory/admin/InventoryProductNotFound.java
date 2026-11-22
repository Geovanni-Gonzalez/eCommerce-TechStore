package com.techstore.inventory.admin;

public final class InventoryProductNotFound extends RuntimeException {
    public InventoryProductNotFound() {
        super("Producto de inventario inexistente");
    }
}
