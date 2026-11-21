package com.techstore.orders.payments;

/** Bloqueos conservadores de stock y casos que requieren investigación humana. */
public enum PaymentIncidentReason {
    AMBIGUOUS_AUTHORIZATION, AWAITING_VERIFIED_APPROVAL,
    LATE_APPROVAL, EVIDENCE_MISMATCH
}
