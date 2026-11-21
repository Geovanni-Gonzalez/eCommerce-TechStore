package com.techstore.payments;

import java.util.Objects;

/** Resultado sin datos sensibles del proveedor. */
public record PaymentAuthorization(
        PaymentOutcome outcome, String providerReference
) {
    public PaymentAuthorization {
        Objects.requireNonNull(outcome, "outcome");
        if (providerReference == null
                || !providerReference.matches("[A-Z0-9-]{8,64}")) {
            throw new IllegalArgumentException("Referencia de pago inválida");
        }
    }
}
