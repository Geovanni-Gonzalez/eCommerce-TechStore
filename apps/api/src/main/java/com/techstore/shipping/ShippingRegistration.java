package com.techstore.shipping;

import java.util.Objects;

/** created=false: reintento seguro que devuelve el mismo registro. */
public record ShippingRegistration(ShipmentRecord shipment, boolean created) {
    public ShippingRegistration {
        Objects.requireNonNull(shipment, "shipment");
    }
}
