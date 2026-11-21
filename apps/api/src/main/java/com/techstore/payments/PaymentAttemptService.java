package com.techstore.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Dos fases: (1) reserva durable bajo lock del pedido y COMMIT,
 * (2) UNA invocación al gateway, seguida por (3) persistencia de respuesta.
 *
 * STARTED/UNKNOWN son estados conciliables, NUNCA se reenvían solos.
 * En BKL-035 solo existen gateway DISABLED y SANDBOX sin dinero real.
 */
@Service
public class PaymentAttemptService {
    private final PaymentAttemptRepository repository;
    private final PaymentGateway gateway;

    public PaymentAttemptService(PaymentAttemptRepository repository,
            PaymentGateway gateway) {
        this.repository = Objects.requireNonNull(repository);
        this.gateway = Objects.requireNonNull(gateway);
    }

    public Submission submit(long userId, long orderId, String key) {
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9_-]{7,79}")) {
            throw new InvalidPaymentAttemptRequestException();
        }

        PaymentAttemptClaim claim = repository.claim(userId, orderId, digest(key));
        if (!claim.created()) {
            // Aun si quedó STARTED/UNKNOWN, repetir no llama a la pasarela.
            return new Submission(claim.attempt(), false);
        }
        PaymentAttemptStatus outcome;
        String reference = null;
        try {
            PaymentAuthorization response = Objects.requireNonNull(
                    gateway.authorize(new PaymentAuthorizationRequest(
                        orderId, claim.attempt().totalCrc(), key)));
            outcome = switch (response.outcome()) {
                case APPROVED -> PaymentAttemptStatus.SIMULATED_APPROVED;
                case DECLINED -> PaymentAttemptStatus.SIMULATED_DECLINED;
            };
            reference = response.providerReference();
        } catch (PaymentGatewayUnavailableException unavailable) {
            outcome = PaymentAttemptStatus.UNAVAILABLE;
        } catch (RuntimeException unknown) {
            // Error ambiguo: NO suponer rechazo/no cobro y NO reintentar.
            outcome = PaymentAttemptStatus.UNKNOWN;
        }
        // Si falla este segundo commit, STARTED permanece para conciliación.
        return new Submission(repository.complete(claim.attempt().id(),
                outcome, reference), true);
    }

    public PaymentAttemptSnapshot findForOwner(long userId, long orderId,
            long attemptId) {
        return repository.findForOwner(userId, orderId, attemptId);
    }

    private static String digest(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 no disponible", failure);
        }
    }

    public record Submission(PaymentAttemptSnapshot attempt, boolean created) { }
}
