package com.techstore.reviews;

import java.time.OffsetDateTime;

/** Reseña pública sin datos de comprador, pedidos ni identificadores privados. */
public record PublicProductReview(int rating, String comment, OffsetDateTime createdAt) { }
