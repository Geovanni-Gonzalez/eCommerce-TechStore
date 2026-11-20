package com.techstore.cart.service;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException() {
        super("El artículo no existe en este carrito");
    }
}
