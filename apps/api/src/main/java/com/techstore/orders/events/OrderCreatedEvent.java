package com.techstore.orders.events;

import java.math.BigDecimal;

/**
 * Evento mínimo sin PII, emitido por el checkout transaccional.
 * El observador concreto lo atiende AFTER_COMMIT, sin outbox.
 */
public record OrderCreatedEvent(long orderId, long userId, BigDecimal totalCrc) { }
