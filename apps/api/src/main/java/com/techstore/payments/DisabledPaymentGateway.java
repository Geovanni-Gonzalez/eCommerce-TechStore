package com.techstore.payments;

import java.util.Objects;

/** Implementación por defecto: nunca afirma que se haya realizado un cobro. */
public final class DisabledPaymentGateway implements PaymentGateway {
    @Override
    public PaymentAuthorization authorize(PaymentAuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        throw new PaymentGatewayUnavailableException();
    }
}
