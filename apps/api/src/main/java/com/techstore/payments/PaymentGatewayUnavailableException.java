package com.techstore.payments;

/** No existe proveedor habilitado: falla de forma explícita y segura. */
public final class PaymentGatewayUnavailableException extends RuntimeException {
    public PaymentGatewayUnavailableException() {
        super("No hay proveedor de pagos habilitado");
    }
}
