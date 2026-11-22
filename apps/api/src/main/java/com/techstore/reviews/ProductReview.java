package com.techstore.reviews;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Vista privada de la reseña propia; no expone correo, usuario ni pedido. */
public record ProductReview(
        long id, long productId, int rating, String comment, OffsetDateTime createdAt
) {
    public ProductReview {
        if (id < 1 || productId < 1 || rating < 1 || rating > 5)
            throw new IllegalArgumentException("Reseña inválida");
        Objects.requireNonNull(comment);
        Objects.requireNonNull(createdAt);
    }
}
