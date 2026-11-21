package com.techstore.orders.payments;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

/** Modo normal no confirma nunca un pedido por callback del sandbox. */
@SpringBootTest
@ActiveProfiles("integration")
class SandboxPaymentConfirmationDisabledIT {
    @Autowired WebApplicationContext context;

    @Test
    void autoConfirmationIsNotExposedByDefault() {
        assertEquals(0, context.getBeanNamesForType(
                SandboxPaymentConfirmationService.class).length);
        assertEquals(0, context.getBeanNamesForType(
                AdminPaymentReconciliationController.class).length);
    }
}
