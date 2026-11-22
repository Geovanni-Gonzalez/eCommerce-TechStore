package com.techstore.reviews;

/**
 * BKL-050: motivo acotado, sin IDs de órdenes, actor ADMIN ni datos personales.
 */
public record ReviewEligibility(
        long productId, boolean eligible, boolean hasActiveReview, String reason
) { }
