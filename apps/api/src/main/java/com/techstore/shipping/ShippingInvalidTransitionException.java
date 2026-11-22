package com.techstore.shipping;

public final class ShippingInvalidTransitionException extends RuntimeException {
    public ShippingInvalidTransitionException() {
        super("Transición logística manual no permitida");
    }
}
