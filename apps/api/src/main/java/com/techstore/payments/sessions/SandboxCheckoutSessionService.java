package com.techstore.payments.sessions;

import com.techstore.payments.InvalidPaymentAttemptRequestException;
import com.techstore.payments.PaymentAttemptClaim;
import com.techstore.payments.PaymentAttemptRepository;
import com.techstore.payments.PaymentAttemptResponse;
import com.techstore.payments.PaymentAttemptSnapshot;
import com.techstore.payments.PaymentAttemptStatus;
import com.techstore.payments.PaymentAuthorization;
import com.techstore.payments.PaymentAuthorizationRequest;
import com.techstore.payments.PaymentGateway;
import com.techstore.payments.PaymentGatewayUnavailableException;
import com.techstore.payments.SandboxPaymentGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout sandbox: autenticación CLIENT al iniciar, claim+sesión atómicos;
 * canje de token confirmado antes de autorizar 1 vez (sin HTTP externo).
 * Nunca confirma pedido ni consume inventario.
 */
@Service
@ConditionalOnProperty(prefix="techstore.payment.sandbox", name="enabled",
        havingValue="true")
public class SandboxCheckoutSessionService {
    private final PaymentAttemptRepository attempts;
    private final SandboxSessionRepository sessions;
    private final PaymentGateway gateway;
    private final SandboxCheckoutRedirectPolicy redirect;

    public SandboxCheckoutSessionService(
            PaymentAttemptRepository attempts, SandboxSessionRepository sessions,
            PaymentGateway gateway,
            @Value("${techstore.payment.sandbox.redirect-origin:http://localhost:3000}")
                    String origin,
            @Value("${techstore.cors.allowed-origins}") String allowedOrigins) {
        this.attempts = Objects.requireNonNull(attempts);
        this.sessions = Objects.requireNonNull(sessions);
        if (!(gateway instanceof SandboxPaymentGateway)) {
            throw new IllegalArgumentException(
                    "El checkout sandbox requiere un PaymentGateway SANDBOX explícito");
        }
        this.gateway = gateway;
        this.redirect = new SandboxCheckoutRedirectPolicy(origin, allowedOrigins);
    }

    /**
     * Asegura claim de la clave y sesión de redirección en un único COMMIT.
     * Repetir la clave rota el token antiguo, sin crear otro intento.
     */
    @Transactional
    public StartResult start(long userId, long orderId, String idempotencyKey) {
        if (idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9_-]{7,79}")) {
            throw new InvalidPaymentAttemptRequestException();
        }
        PaymentAttemptClaim claimed = attempts.claim(
                userId, orderId, digest(idempotencyKey));
        String token = SandboxSessionToken.generate();
        SandboxSessionSnapshot session = sessions.issue(
                orderId, claimed.attempt().id(), SandboxSessionToken.hash(token));
        var response = new SandboxSessionStartedResponse(
                session.attemptId(), session.orderId(), session.totalCrc(),
                session.currency(), session.expiresAt(), redirect.redirectUrl(token));
        return new StartResult(response, claimed.created());
    }

    public SandboxSessionSnapshot preview(String token) {
        return sessions.preview(SandboxSessionToken.hash(token));
    }

    /**
     * Consume el token en BD ANTES de llamar al sandbox. Al fallar/cerrar
     * el proceso, STARTED queda pendiente de conciliación; nunca reenviar.
     */
    public PaymentAttemptResponse complete(String token) {
        SandboxSessionSnapshot consumed =
                sessions.redeem(SandboxSessionToken.hash(token));
        PaymentAttemptStatus status;
        String reference = null;
        try {
            String gatewayKey = "hosted-sandbox-" + consumed.attemptId();
            PaymentAuthorization authorization = Objects.requireNonNull(
                    gateway.authorize(new PaymentAuthorizationRequest(
                        consumed.orderId(), consumed.totalCrc(), gatewayKey)));
            status = switch (authorization.outcome()) {
                case APPROVED -> PaymentAttemptStatus.SIMULATED_APPROVED;
                case DECLINED -> PaymentAttemptStatus.SIMULATED_DECLINED;
            };
            reference = authorization.providerReference();
        } catch (PaymentGatewayUnavailableException disabled) {
            status = PaymentAttemptStatus.UNAVAILABLE;
        } catch (RuntimeException unknown) {
            status = PaymentAttemptStatus.UNKNOWN;
        }
        PaymentAttemptSnapshot recorded = attempts.complete(
                consumed.attemptId(), status, reference);
        return PaymentAttemptResponse.of(recorded);
    }

    private static String digest(String raw) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 no disponible", failure);
        }
    }

    public record StartResult(SandboxSessionStartedResponse response,
            boolean attemptCreated) { }
}
