package com.techstore.payments;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Foto inmutable del intento, sin clave de idempotencia ni PII. */
public record PaymentAttemptSnapshot(
        long id, long orderId, BigDecimal totalCrc,
        PaymentAttemptStatus status, String providerReference,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) { }
