package com.techstore.payments.webhook;

import com.techstore.orders.domain.CrcMoney;
import com.techstore.payments.PaymentOutcome;
import java.math.BigDecimal;
import java.util.Objects;

/** Solo datos firmados ya verificados; no contiene información de tarjeta. */
public record VerifiedSandboxWebhookEvent(
        String eventId, String providerReference, long orderId,
        BigDecimal totalCrc, PaymentOutcome outcome
) {
    public VerifiedSandboxWebhookEvent {
        if (eventId == null ||
                !eventId.matches("[A-Za-z0-9][A-Za-z0-9_-]{7,79}")
                || providerReference == null
                || !providerReference.matches("SBX-[A-Z0-9-]{8,60}")
                || orderId < 1) {
            throw new InvalidWebhookEventException();
        }
        try {
            totalCrc = CrcMoney.require(totalCrc, 14, "totalCrc");
        } catch (IllegalArgumentException invalid) {
            throw new InvalidWebhookEventException();
        }
        if (totalCrc.signum() <= 0 || outcome == null) {
            throw new InvalidWebhookEventException();
        }
    }
}
