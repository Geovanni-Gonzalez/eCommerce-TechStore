package com.techstore.cart.service;

public class CartProductNotFoundException extends RuntimeException {
    public CartProductNotFoundException() {
        super("El producto no existe o no está activo");
    }
}
