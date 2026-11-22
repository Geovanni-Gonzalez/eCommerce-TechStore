package com.techstore.shipping;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Registro mínimo sin dirección, teléfono, proveedor o tracking ficticio. */
public record ShipmentRecord(
        long id,
        long orderId,
        String fulfillmentMethod,
        ShippingStatus status,
        long openedByUserId,
        OffsetDateTime registeredAt
) {
    public ShipmentRecord {
        if (id < 1 || orderId < 1 || openedByUserId < 1) {
            throw new IllegalArgumentException("Identificador logístico no válido");
        }
        if (!"MANUAL".equals(fulfillmentMethod)) {
            throw new IllegalArgumentException("Solo se permite despacho manual");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }
}
