package com.techstore.payments;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprueba la política por defecto de la aplicación completa:
 * el arranque no habilita simulación de aprobación accidentalmente.
 */
@SpringBootTest
@ActiveProfiles("integration")
class PaymentGatewayWiringIT {
    @Autowired PaymentGateway paymentGateway;

    @Test
    void defaultSpringWiringKeepsPaymentsDisabled() {
        assertInstanceOf(DisabledPaymentGateway.class, paymentGateway);
        assertThrows(PaymentGatewayUnavailableException.class,
                () -> paymentGateway.authorize(new PaymentAuthorizationRequest(
                        1, new java.math.BigDecimal("3000.00"), "attempt-2026-0003")));
    }
}
