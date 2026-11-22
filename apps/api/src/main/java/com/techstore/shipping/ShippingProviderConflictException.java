package com.techstore.shipping;

/** Impide tomar un envío MANUAL como SIMULATOR o viceversa. */
public final class ShippingProviderConflictException extends RuntimeException {
    public ShippingProviderConflictException() {
        super("El pedido ya tiene una gestión logística de otro modo");
    }
}
