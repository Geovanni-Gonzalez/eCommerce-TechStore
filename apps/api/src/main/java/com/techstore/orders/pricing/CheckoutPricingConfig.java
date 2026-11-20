package com.techstore.orders.pricing;

import com.techstore.orders.shipping.FixedShippingStrategy;
import com.techstore.orders.shipping.FreeOverThresholdShippingStrategy;
import com.techstore.orders.shipping.ShippingStrategy;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selección de la estrategia por configuración. Cambiar la política de envío
 * no modifica CheckoutTotalsCalculator (GoF Strategy + SOLID OCP/DIP).
 */
@Configuration
public class CheckoutPricingConfig {
    @Bean
    ShippingStrategy shippingStrategy(
            @Value("${techstore.checkout.shipping-policy:FREE_OVER_THRESHOLD}")
                    String policy,
            @Value("${techstore.checkout.shipping-fee-crc:2500.00}")
                    BigDecimal feeCrc,
            @Value("${techstore.checkout.free-shipping-threshold-crc:30000.00}")
                    BigDecimal thresholdCrc) {
        return selectPolicy(policy, feeCrc, thresholdCrc);
    }

    /**
     * Visible para pruebas y configuración: falla al arrancar ante políticas
     * desconocidas en lugar de aplicar silenciosamente una regla incorrecta.
     */
    public static ShippingStrategy selectPolicy(
            String policy, BigDecimal feeCrc, BigDecimal thresholdCrc) {
        if (policy == null) {
            throw new IllegalArgumentException("La política de envío es obligatoria");
        }
        return switch (policy.strip().toUpperCase(Locale.ROOT)) {
            case "FIXED" -> new FixedShippingStrategy(feeCrc);
            case "FREE_OVER_THRESHOLD" ->
                    new FreeOverThresholdShippingStrategy(feeCrc, thresholdCrc);
            default -> throw new IllegalArgumentException(
                    "Política de envío desconocida");
        };
    }

    @Bean
    CheckoutTotalsCalculator checkoutTotalsCalculator(
            ShippingStrategy shippingStrategy,
            @Value("${techstore.checkout.tax-rate:0.13}")
                    BigDecimal taxRate) {
        return new CheckoutTotalsCalculator(shippingStrategy, taxRate);
    }
}
