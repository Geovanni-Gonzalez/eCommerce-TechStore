package com.techstore.orders.payments;

/**
 * Requiere lock de orders ANTES de ejecutarse. Si retorna un motivo,
 * NO es seguro liberar reserva ni cancelar mientras no se concilie.
 */
public interface PaymentExposurePolicy {
    PaymentIncidentReason blockingReason(long orderId);
}
