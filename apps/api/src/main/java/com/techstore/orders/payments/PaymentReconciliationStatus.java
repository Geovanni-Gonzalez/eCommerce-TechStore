package com.techstore.orders.payments;

/** Evidencia local: el sandbox nunca implica fondos bancarios recibidos. */
public enum PaymentReconciliationStatus {
    RECEIVED, CONFIRMED_SIMULATED, DECLINED, NEEDS_REVIEW
}
