package com.techstore.orders.payments;

import com.techstore.payments.PaymentGateway;
import com.techstore.payments.SandboxPaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Reconciliación solo de la simulación local, deshabilitada por defecto.
 * Para operación bancaria real hay que crear OTRO adapter, sin reutilizar
 * firmas secretas de sandbox como prueba de fondos.
 */
@Service
@ConditionalOnProperty(prefix="techstore.payment.sandbox",
        name="auto-confirm-verified-events", havingValue="true")
public class SandboxPaymentConfirmationService {
    private final JdbcSimulatedPaymentConfirmationRepository repository;

    public SandboxPaymentConfirmationService(
            JdbcSimulatedPaymentConfirmationRepository repository,
            PaymentGateway gateway,
            @Value("${techstore.payment.sandbox.enabled:false}") boolean sandboxEnabled,
            @Value("${techstore.payment.webhook.enabled:false}") boolean webhookEnabled) {
        if (!sandboxEnabled || !webhookEnabled
                || !(gateway instanceof SandboxPaymentGateway)) {
            throw new IllegalStateException(
                    "Confirmación sandbox requiere pasarela SANDBOX y webhook HMAC habilitados");
        }
        this.repository = repository;
    }

    public PaymentReconciliationResult reconcile(long eventId) {
        return repository.reconcile(eventId);
    }
}
