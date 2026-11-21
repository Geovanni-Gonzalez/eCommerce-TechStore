package com.techstore.payments;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Respuesta sin clave de idempotencia, token, PAN/CVV ni datos de usuario. */
public record PaymentAttemptResponse(
        long id, long orderId, BigDecimal totalCrc, PaymentAttemptStatus status,
        String providerReference, OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PaymentAttemptResponse of(PaymentAttemptSnapshot value) {
        return new PaymentAttemptResponse(value.id(), value.orderId(),
                value.totalCrc(), value.status(), value.providerReference(),
                value.createdAt(), value.updatedAt());
    }
}
