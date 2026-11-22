package com.techstore.reviews;

import java.util.Map;

/** Validación exacta: el cliente no suministra userId, orderId ni active. */
public record ReviewInput(int rating, String comment) {
    public ReviewInput {
        if (rating < 1 || rating > 5 || comment == null
                || comment.length() < 5 || comment.length() > 1000
                || !comment.equals(comment.strip())
                || comment.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidReviewRequestException();
        }
    }

    public static ReviewInput parse(Map<String, Object> raw) {
        if (raw == null || raw.size() != 2 || !raw.containsKey("rating")
                || !raw.containsKey("comment")
                || !(raw.get("comment") instanceof String comment)
                || !(raw.get("rating") instanceof Integer rating)) {
            throw new InvalidReviewRequestException();
        }
        return new ReviewInput(rating, comment);
    }
}
