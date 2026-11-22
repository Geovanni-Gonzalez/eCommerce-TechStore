package com.techstore.shipping;

import java.util.Objects;

/**
 * Simulador explícito de BKL-052. No existe integración de transporte real.
 * AVAILABLE registra eventos ficticios y marcados SIMULATOR; UNAVAILABLE
 * lanza una excepción antes de cualquier escritura en PostgreSQL.
 */
public final class SimulatedShippingGateway implements ShippingGateway {
    private final JdbcShipmentRepository shipments;
    private final ShippingSimulatorOutcome outcome;

    public SimulatedShippingGateway(
            JdbcShipmentRepository shipments, ShippingSimulatorOutcome outcome) {
        this.shipments=Objects.requireNonNull(shipments);
        this.outcome=Objects.requireNonNull(outcome);
    }

    @Override
    public void verifyExisting(ShipmentRecord record) {
        Objects.requireNonNull(record);
        requireAvailable();
        if (!"SIMULATOR".equals(record.fulfillmentMethod())) {
            throw new ShippingProviderConflictException();
        }
    }

    @Override
    public ShippingRegistration register(ShippingRegistrationCommand command) {
        Objects.requireNonNull(command);
        requireAvailable();
        return shipments.registerSimulated(command);
    }

    @Override
    public ShipmentRecord advance(ShippingTransitionCommand command) {
        Objects.requireNonNull(command);
        requireAvailable();
        return shipments.advanceSimulated(command);
    }

    private void requireAvailable() {
        if (outcome!=ShippingSimulatorOutcome.AVAILABLE) {
            throw new ShippingGatewayUnavailableException();
        }
    }
}
