package com.techstore.reviews;

public final class ReviewNotEligibleException extends RuntimeException {
    public ReviewNotEligibleException() {
        super("Sin entrega MANUAL acreditada o ya existe una reseña activa");
    }
}
