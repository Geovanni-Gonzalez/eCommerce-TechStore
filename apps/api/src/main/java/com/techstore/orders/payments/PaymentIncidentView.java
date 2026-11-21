package com.techstore.orders.payments;

import java.time.OffsetDateTime;

/** Datos mínimos para ADMIN; sin secretos, tarjetas ni información del cliente. */
public record PaymentIncidentView(
        long id, long orderId, PaymentIncidentReason reason,
        Long webhookEventId, OffsetDateTime detectedAt
) { }
