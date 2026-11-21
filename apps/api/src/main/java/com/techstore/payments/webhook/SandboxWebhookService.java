package com.techstore.payments.webhook;

import com.techstore.orders.domain.CrcMoney;
import com.techstore.payments.PaymentOutcome;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Primero verifica firma sobre bytes sin modificar, luego parsea y valida.
 * No realiza cambios de pedidos/reservas; solo inserta evidencia verificable.
 */
@Service
@ConditionalOnProperty(prefix = "techstore.payment.webhook", name = "enabled",
        havingValue = "true")
public class SandboxWebhookService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final SignedSandboxWebhookVerifier verifier;
    private final VerifiedWebhookEventRepository repository;

    public SandboxWebhookService(SignedSandboxWebhookVerifier verifier,
            VerifiedWebhookEventRepository repository) {
        this.verifier = Objects.requireNonNull(verifier);
        this.repository = Objects.requireNonNull(repository);
    }

    public WebhookEventReceipt receive(
            byte[] rawBody, String timestamp, String signature) {
        verifier.verify(rawBody, timestamp, signature);
        VerifiedSandboxWebhookEvent event = parse(rawBody);
        return repository.record(event, digest(rawBody));
    }

    static VerifiedSandboxWebhookEvent parse(byte[] rawBody) {
        try {
            String body = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawBody)).toString();
            JsonNode json = JSON.readTree(body);
            if (json == null || !json.isObject() || json.size() != 5) {
                throw new InvalidWebhookEventException();
            }
            JsonNode eventId = json.get("eventId");
            JsonNode reference = json.get("providerReference");
            JsonNode orderId = json.get("orderId");
            JsonNode total = json.get("totalCrc");
            JsonNode outcome = json.get("outcome");
            if (eventId == null || !eventId.isTextual()
                    || reference == null || !reference.isTextual()
                    || orderId == null || !orderId.isIntegralNumber()
                    || total == null || !total.isNumber()
                    || outcome == null || !outcome.isTextual()) {
                throw new InvalidWebhookEventException();
            }
            PaymentOutcome parsedOutcome = switch (outcome.asText()) {
                case "APPROVED" -> PaymentOutcome.APPROVED;
                case "DECLINED" -> PaymentOutcome.DECLINED;
                default -> throw new InvalidWebhookEventException();
            };
            return new VerifiedSandboxWebhookEvent(
                    eventId.asText(), reference.asText(), orderId.asLong(),
                    new BigDecimal(total.asText()), parsedOutcome);
        } catch (InvalidWebhookEventException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new InvalidWebhookEventException();
        }
    }

    private static String digest(byte[] rawBody) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rawBody));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible", impossible);
        }
    }
}
