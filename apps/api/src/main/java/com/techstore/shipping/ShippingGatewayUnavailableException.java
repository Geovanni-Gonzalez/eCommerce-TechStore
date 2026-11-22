package com.techstore.shipping;

/** Proveedor desactivado: falla cerrado, jamás simula un envío real. */
public final class ShippingGatewayUnavailableException extends RuntimeException {
    public ShippingGatewayUnavailableException() {
        super("El registro de envíos no está habilitado");
    }
}
