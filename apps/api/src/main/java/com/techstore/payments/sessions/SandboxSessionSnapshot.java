package com.techstore.payments.sessions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Resumen público del checkout simulado, sin comprador ni tarjeta. */
public record SandboxSessionSnapshot(
        long attemptId, long orderId, BigDecimal totalCrc,
        String currency, OffsetDateTime expiresAt
) { }
