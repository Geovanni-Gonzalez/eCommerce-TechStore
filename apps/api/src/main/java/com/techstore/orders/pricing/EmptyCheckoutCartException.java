package com.techstore.orders.pricing;

/** La cotización necesita al menos un artículo (422). */
public final class EmptyCheckoutCartException extends RuntimeException {
    public EmptyCheckoutCartException() {
        super("El carrito está vacío");
    }
}
