package com.techstore.payments.sessions;

import com.techstore.payments.InvalidPaymentAttemptRequestException;
import com.techstore.payments.PaymentAttemptResponse;
import com.techstore.users.security.SessionPrincipal;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inicio con Bearer CLIENT; preview/complete admiten solo token opaco de
 * sesión, no cookies y no credenciales ni precios en request.
 */
@RestController
@ConditionalOnProperty(prefix="techstore.payment.sandbox", name="enabled",
        havingValue="true")
public class SandboxCheckoutSessionController {
    private final SandboxCheckoutSessionService service;

    public SandboxCheckoutSessionController(SandboxCheckoutSessionService service) {
        this.service = Objects.requireNonNull(service);
    }

    @PostMapping(path="/api/orders/{orderId}/payment-sessions")
    public ResponseEntity<SandboxSessionStartedResponse> start(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId,
            @RequestHeader(name="Idempotency-Key", required=false)
                    String idempotencyKey,
            @RequestBody(required=false) String body) {
        if (body != null && !body.isBlank()) {
            throw new InvalidPaymentAttemptRequestException();
        }
        SandboxCheckoutSessionService.StartResult issued = service.start(
                principal.id(), orderId, idempotencyKey);
        return ResponseEntity.status(
                issued.attemptCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(issued.response());
    }

    @PostMapping(path="/api/payments/sandbox/sessions/preview",
            consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SandboxSessionSnapshot> preview(
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(service.preview(validToken(body)));
    }

    @PostMapping(path="/api/payments/sandbox/sessions/complete",
            consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentAttemptResponse> complete(
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(service.complete(validToken(body)));
    }

    private static String validToken(Map<String, Object> body) {
        if (body == null || body.size() != 1
                || !(body.get("token") instanceof String token)
                || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new SandboxSessionInvalidRequestException();
        }
        return token;
    }
}
