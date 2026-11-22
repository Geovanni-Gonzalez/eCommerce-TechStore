package com.techstore.shipping;

import java.util.Objects;

/**
 * Alternativa manual controlada: únicamente registra la tarea pendiente.
 * NO remite paquetes, emite tracking, envía correo ni marca DELIVERED.
 */
public final class ManualShippingGateway implements ShippingGateway {
    private final JdbcShipmentRepository shipments;

    public ManualShippingGateway(JdbcShipmentRepository shipments) {
        this.shipments = Objects.requireNonNull(shipments, "shipments");
    }

    @Override
    public void verifyExisting(ShipmentRecord shipment) {
        Objects.requireNonNull(shipment);
        if (!"MANUAL".equals(shipment.fulfillmentMethod()))
            throw new ShippingProviderConflictException();
    }

    @Override
    public ShipmentRecord advance(ShippingTransitionCommand command) {
        Objects.requireNonNull(command, "command");
        return shipments.advanceManual(command);
    }

    @Override
    public ShippingRegistration register(ShippingRegistrationCommand command) {
        Objects.requireNonNull(command, "command");
        return shipments.registerManual(command);
    }
}
