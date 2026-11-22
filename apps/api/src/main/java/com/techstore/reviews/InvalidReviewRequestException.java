package com.techstore.reviews;

public final class InvalidReviewRequestException extends RuntimeException {
    public InvalidReviewRequestException() {
        super("La calificación y el comentario no son válidos");
    }
}
