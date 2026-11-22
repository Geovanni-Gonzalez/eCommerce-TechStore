package com.techstore.reviews;

public final class ReviewProductNotFoundException extends RuntimeException {
    public ReviewProductNotFoundException() {
        super("Producto no encontrado");
    }
}
