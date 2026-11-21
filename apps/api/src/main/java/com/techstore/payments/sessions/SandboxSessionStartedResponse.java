package com.techstore.payments.sessions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** URL de destino fija; token solo en fragmento, no se conserva en BD. */
public record SandboxSessionStartedResponse(
        long attemptId, long orderId, BigDecimal totalCrc, String currency,
        OffsetDateTime expiresAt, String redirectUrl
) { }
