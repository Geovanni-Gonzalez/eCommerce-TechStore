package com.techstore.orders.payments;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reintento operativo ADMIN del evento ya firmado y persistido:
 * nunca admite cuerpos, importes, referencias o resultados inventados.
 */
@RestController
@RequestMapping("/api/admin/payment-events")
@ConditionalOnProperty(prefix="techstore.payment.sandbox",
        name="auto-confirm-verified-events", havingValue="true")
public class AdminPaymentReconciliationController {
    private final SandboxPaymentConfirmationService confirmation;

    public AdminPaymentReconciliationController(SandboxPaymentConfirmationService confirmation) {
        this.confirmation = confirmation;
    }

    @PostMapping("/{eventId}/reconcile")
    public ResponseEntity<PaymentReconciliationResult> reconcile(
            @PathVariable long eventId,
            @RequestBody(required=false) String body) {
        if (body != null && !body.isBlank()) {
            throw new InvalidPaymentReconciliationRequestException();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(confirmation.reconcile(eventId));
    }
}
