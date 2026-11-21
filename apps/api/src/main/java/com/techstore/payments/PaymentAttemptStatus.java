package com.techstore.payments;

/**
 * APPROVED es solo simulación local; no acredita pago liquidado.
 * UNKNOWN/STARTED impiden repetir autorización sin conciliación.
 */
public enum PaymentAttemptStatus {
    STARTED, SIMULATED_APPROVED, SIMULATED_DECLINED, UNAVAILABLE, UNKNOWN
}
