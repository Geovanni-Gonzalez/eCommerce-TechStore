package com.techstore.cart.service;

public class CartConflictException extends RuntimeException {
    public CartConflictException() {
        super("La cantidad solicitada supera el stock o el límite del carrito");
    }
}
