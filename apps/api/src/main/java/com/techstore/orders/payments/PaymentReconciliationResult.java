package com.techstore.orders.payments;

/**
 * Confirmación de simulación, no recibo de dinero real.
 * needsReview incluye pagos tardíos, reservas vencidas o datos inconsistentes.
 */
public record PaymentReconciliationResult(
        long eventId, long orderId, PaymentReconciliationStatus status
) { }
