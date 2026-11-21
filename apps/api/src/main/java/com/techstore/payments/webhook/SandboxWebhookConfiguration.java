package com.techstore.payments.webhook;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La función no existe por defecto. Al habilitarla sin secreto suficientemente
 * fuerte, el backend falla al arrancar en lugar de aceptar eventos falsos.
 */
@Configuration
@ConditionalOnProperty(prefix = "techstore.payment.webhook", name = "enabled",
        havingValue = "true")
public class SandboxWebhookConfiguration {
    @Bean
    SignedSandboxWebhookVerifier signedSandboxWebhookVerifier(
            @Value("${techstore.payment.webhook.secret:}") String secret) {
        return new SignedSandboxWebhookVerifier(secret, Clock.systemUTC());
    }
}
