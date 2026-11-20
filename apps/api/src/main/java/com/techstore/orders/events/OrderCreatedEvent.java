package com.techstore.orders.events;

import java.math.BigDecimal;

/**
 * Evento mínimo sin PII para un Observer posterior.
 * Se publica al crear un pedido; sus futuros listeners deberán usar
 * @TransactionalEventListener(phase = AFTER_COMMIT).
 */
public record OrderCreatedEvent(long orderId, long userId, BigDecimal totalCrc) { }
