package com.techstore.shipping;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** NOT_REGISTERED significa ausencia de tarea, nunca entrega realizada. */
public record ShippingTrackingView(
        long orderId,String status,OffsetDateTime registeredAt,
        List<ShippingCustomerEvent> events
) {
    public ShippingTrackingView {
        if(orderId<1)throw new IllegalArgumentException("Pedido inválido");
        Objects.requireNonNull(status);
        events=List.copyOf(events);
    }
}
