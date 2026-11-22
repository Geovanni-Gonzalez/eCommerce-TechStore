package com.techstore.shipping;

/** Solo IDs resueltos en la API: nunca dirección, transportista o pagos. */
public record ShippingRegistrationCommand(long orderId, long actorUserId) {
    public ShippingRegistrationCommand {
        if (orderId < 1 || actorUserId < 1) {
            throw new IllegalArgumentException("IDs de pedido/ADMIN inválidos");
        }
    }
}
