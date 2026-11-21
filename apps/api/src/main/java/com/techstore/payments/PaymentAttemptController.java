package com.techstore.payments;

import com.techstore.users.security.SessionPrincipal;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP del prototipo: genera intento, jamás cobra dinero real ni confirma
 * pedido. El monto siempre procede del snapshot de orders en PostgreSQL.
 */
@RestController
@RequestMapping("/api/orders")
public class PaymentAttemptController {
    private final PaymentAttemptService service;

    public PaymentAttemptController(PaymentAttemptService service) {
        this.service = Objects.requireNonNull(service);
    }

    @PostMapping(path="/{orderId}/payment-attempts")
    public ResponseEntity<PaymentAttemptResponse> submit(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId,
            @RequestHeader(name="Idempotency-Key", required=false) String key,
            @RequestBody(required=false) String payload) {
        if (payload != null && !payload.isBlank()) {
            throw new InvalidPaymentAttemptRequestException();
        }
        PaymentAttemptService.Submission result =
                service.submit(principal.id(), orderId, key);
        PaymentAttemptSnapshot attempt = result.attempt();
        var response = ResponseEntity.status(
                result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore());
        if (result.created()) {
            response.location(URI.create("/api/orders/" + orderId
                    + "/payment-attempts/" + attempt.id()));
        }
        return response.body(PaymentAttemptResponse.of(attempt));
    }

    @GetMapping(path="/{orderId}/payment-attempts/{attemptId}",
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentAttemptResponse> find(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId, @PathVariable long attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PaymentAttemptResponse.of(
                        service.findForOwner(principal.id(), orderId, attemptId)));
    }
}
