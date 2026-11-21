package com.techstore.orders.history;

/** Motivo estable; BASELINE no supone que existan transiciones históricas. */
public enum OrderChangeReason {
    CREATED, BASELINE, CLIENT_CANCEL, ADMIN_CANCEL, EXPIRED, SANDBOX_VERIFIED
}
