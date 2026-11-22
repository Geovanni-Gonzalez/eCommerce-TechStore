package com.techstore.shipping;

public final class ShippingOrderNotConfirmedException extends RuntimeException {
    public ShippingOrderNotConfirmedException() {
        super("Solo puede prepararse la gestión logística de pedidos CONFIRMED");
    }
}
