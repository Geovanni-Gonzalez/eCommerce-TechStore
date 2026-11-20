package com.techstore.cart.service;

public class CartValidationException extends IllegalArgumentException {
    public CartValidationException(String message) {
        super(message);
    }
}
