package com.techstore.shipping;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DISABLED por defecto. MANUAL permite declaración operativa.
 * SIMULATOR exige opt-in + resultado AVAILABLE/UNAVAILABLE explícito.
 * Nunca contacta transportistas ni confirma entregas reales.
 */
@Configuration
public class ShippingGatewayConfig {
    @Bean
    ShippingGateway shippingGateway(
            @Value("${techstore.shipping.provider:DISABLED}") String provider,
            @Value("${techstore.shipping.simulator.enabled:false}") boolean simulatorEnabled,
            @Value("${techstore.shipping.simulator.outcome:UNAVAILABLE}") String simulatorOutcome,
            JdbcShipmentRepository shipments) {
        return selectProvider(provider, simulatorEnabled, simulatorOutcome, shipments);
    }

    public static ShippingGateway selectProvider(
            String provider, boolean simulatorEnabled, String simulatorOutcome,
            JdbcShipmentRepository shipments) {
        if (provider == null) {
            throw new IllegalArgumentException("Proveedor logístico obligatorio");
        }
        return switch (provider.strip().toUpperCase(Locale.ROOT)) {
            case "DISABLED" -> new DisabledShippingGateway();
            case "MANUAL" -> new ManualShippingGateway(shipments);
            case "SIMULATOR" -> {
                if (!simulatorEnabled) {
                    throw new IllegalArgumentException(
                            "El simulador requiere activación explícita");
                }
                if (simulatorOutcome == null) {
                    throw new IllegalArgumentException("Resultado del simulador obligatorio");
                }
                ShippingSimulatorOutcome outcome = switch (
                        simulatorOutcome.strip().toUpperCase(Locale.ROOT)) {
                    case "AVAILABLE" -> ShippingSimulatorOutcome.AVAILABLE;
                    case "UNAVAILABLE" -> ShippingSimulatorOutcome.UNAVAILABLE;
                    default -> throw new IllegalArgumentException(
                            "Resultado de simulador desconocido");
                };
                yield new SimulatedShippingGateway(shipments, outcome);
            }
            default -> throw new IllegalArgumentException(
                    "Proveedor logístico desconocido");
        };
    }
}
