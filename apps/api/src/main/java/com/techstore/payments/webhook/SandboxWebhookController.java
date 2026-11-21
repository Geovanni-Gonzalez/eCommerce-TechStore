package com.techstore.payments.webhook;

import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Única excepción a Bearer: un callback SOLO de sandbox con HMAC de secreto
 * externo y timestamp reciente. Deshabilitado por defecto.
 */
@RestController
@RequestMapping("/api/payments/webhooks")
@ConditionalOnProperty(prefix = "techstore.payment.webhook", name = "enabled",
        havingValue = "true")
public class SandboxWebhookController {
    private final SandboxWebhookService service;

    public SandboxWebhookController(SandboxWebhookService service) {
        this.service = Objects.requireNonNull(service);
    }

    @PostMapping(path="/sandbox", consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebhookEventReceipt> accept(
            @RequestHeader(name="X-TechStore-Webhook-Timestamp", required=false)
                    String timestamp,
            @RequestHeader(name="X-TechStore-Webhook-Signature", required=false)
                    String signature,
            @RequestBody byte[] body) {
        WebhookEventReceipt receipt = service.receive(body, timestamp, signature);
        return ResponseEntity.status(receipt.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore()).body(receipt);
    }
}
