package com.techstore.payments;

/** Clave reutilizada con otro pedido o intento activo/final incompatible. */
public final class PaymentAttemptConflictException extends RuntimeException {
    public PaymentAttemptConflictException() {
        super("Intento incompatible o pedido no disponible para un pago nuevo");
    }
}
