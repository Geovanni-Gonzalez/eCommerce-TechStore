package com.techstore.shipping;

import java.util.Objects;

/** El actor se obtiene del principal autenticado, nunca del JSON. */
public record ShippingTransitionCommand(
        long orderId,long actorUserId,ShippingStatus fromStatus,ShippingStatus toStatus
) {
    public ShippingTransitionCommand {
        if(orderId<1||actorUserId<1)throw new IllegalArgumentException("IDs inválidos");
        Objects.requireNonNull(fromStatus);
        Objects.requireNonNull(toStatus);
        if(!fromStatus.canAdvanceTo(toStatus))throw new ShippingInvalidTransitionException();
    }
}
