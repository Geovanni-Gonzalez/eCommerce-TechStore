package com.techstore.shipping;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DISABLED por defecto. MANUAL requiere opt-in explícito:
 * TECHSTORE_SHIPPING_PROVIDER=MANUAL.
 * Los proveedores ajenos se rechazan (fail-closed).
 */
@Configuration
public class ShippingGatewayConfig {
    @Bean
    ShippingGateway shippingGateway(
            @Value("${techstore.shipping.provider:DISABLED}") String provider,
            JdbcShipmentRepository shipments) {
        return selectProvider(provider, shipments);
    }

    public static ShippingGateway selectProvider(
            String provider, JdbcShipmentRepository shipments) {
        if (provider == null) {
            throw new IllegalArgumentException("Proveedor logístico obligatorio");
        }
        return switch (provider.strip().toUpperCase(Locale.ROOT)) {
            case "DISABLED" -> new DisabledShippingGateway();
            case "MANUAL" -> new ManualShippingGateway(shipments);
            default -> throw new IllegalArgumentException(
                    "Proveedor logístico desconocido (solo DISABLED/MANUAL)");
        };
    }
}
