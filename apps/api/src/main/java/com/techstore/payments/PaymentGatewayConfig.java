package com.techstore.payments;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selección explícita y fail-closed. El sandbox debe habilitarse expresamente;
 * ningún proveedor externo está configurado ni se esperan secretos en el repo.
 */
@Configuration
public class PaymentGatewayConfig {
    @Bean
    PaymentGateway paymentGateway(
            @Value("${techstore.payment.provider:DISABLED}") String provider,
            @Value("${techstore.payment.sandbox.enabled:false}") boolean enabled,
            @Value("${techstore.payment.sandbox.outcome:DECLINED}") String outcome) {
        return selectProvider(provider, enabled, outcome);
    }

    public static PaymentGateway selectProvider(
            String provider, boolean sandboxEnabled, String sandboxOutcome) {
        if (provider == null) {
            throw new IllegalArgumentException("Proveedor de pagos obligatorio");
        }
        return switch (provider.strip().toUpperCase(Locale.ROOT)) {
            case "DISABLED" -> new DisabledPaymentGateway();
            case "SANDBOX" -> {
                if (!sandboxEnabled) {
                    throw new IllegalArgumentException(
                            "El sandbox requiere activación explícita");
                }
                if (sandboxOutcome == null) {
                    throw new IllegalArgumentException("Resultado sandbox obligatorio");
                }
                PaymentOutcome outcome = switch (
                        sandboxOutcome.strip().toUpperCase(Locale.ROOT)) {
                    case "APPROVED" -> PaymentOutcome.APPROVED;
                    case "DECLINED" -> PaymentOutcome.DECLINED;
                    default -> throw new IllegalArgumentException(
                            "Resultado sandbox desconocido");
                };
                yield new SandboxPaymentGateway(outcome);
            }
            default -> throw new IllegalArgumentException(
                    "Proveedor de pagos desconocido");
        };
    }
}
