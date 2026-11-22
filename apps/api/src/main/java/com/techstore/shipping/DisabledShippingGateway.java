package com.techstore.shipping;

import java.util.Objects;

/** Configuración por defecto; no crea filas ni invoca integraciones externas. */
public final class DisabledShippingGateway implements ShippingGateway {
    @Override
    public void verifyExisting(ShipmentRecord shipment) {
        Objects.requireNonNull(shipment);
        throw new ShippingGatewayUnavailableException();
    }

    @Override
    public ShipmentRecord advance(ShippingTransitionCommand command) {
        Objects.requireNonNull(command, "command");
        throw new ShippingGatewayUnavailableException();
    }

    @Override
    public ShippingRegistration register(ShippingRegistrationCommand command) {
        Objects.requireNonNull(command, "command");
        throw new ShippingGatewayUnavailableException();
    }
}
