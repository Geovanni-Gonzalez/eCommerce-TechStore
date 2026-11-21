package com.techstore.payments.webhook;

/** Respuesta mínima: ningún movimiento de fondos o de inventario. */
public record WebhookEventReceipt(
        long id, String eventId, long paymentAttemptId,
        boolean duplicate
) { }
