package com.techstore.orders.inventory;

public final class InsufficientStockException extends RuntimeException {
    public InsufficientStockException() {
        super("Al menos un producto no está disponible en la cantidad solicitada");
    }
}
