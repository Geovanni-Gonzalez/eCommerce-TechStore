package com.techstore.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Simulador sin red, pasarela real, credenciales, llamadas HTTP ni cobros.
 * Para el MISMO intento y monto devuelve la misma referencia en cada llamada.
 *
 * NO persiste idempotencia ni garantiza unicidad entre intentos: BKL-036.
 */
public final class SandboxPaymentGateway implements PaymentGateway {
    private final PaymentOutcome outcome;

    public SandboxPaymentGateway(PaymentOutcome outcome) {
        this.outcome = Objects.requireNonNull(outcome);
    }

    @Override
    public PaymentAuthorization authorize(PaymentAuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        String canonical = "techstore-sandbox-v1|" + request.orderId() + "|"
                + request.totalCrc().toPlainString() + "|" + request.idempotencyKey();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            String reference = "SBX-" + HexFormat.of().withUpperCase()
                    .formatHex(hash, 0, 12);
            return new PaymentAuthorization(outcome, reference);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 no disponible", failure);
        }
    }
}
