package com.techstore.shipping;

public final class ShippingNotFoundException extends RuntimeException {
    public ShippingNotFoundException() {
        super("Pedido o registro logístico no encontrado");
    }
}
