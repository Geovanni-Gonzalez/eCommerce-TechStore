package com.techstore.payments.webhook;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Firma del payload exacto, ventana anti-replay y secretos obligatorios. */
class SignedSandboxWebhookVerifierTest {
    private static final String SECRET = "test-only-webhook-hmac-key-0123456789abcdef";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptsOnlyExactSignedBytesWithinFiveMinutes() throws Exception {
        var verifier = new SignedSandboxWebhookVerifier(SECRET, CLOCK);
        byte[] body = "{\"eventId\":\"event-0001\"}".getBytes(StandardCharsets.UTF_8);
        long now = CLOCK.instant().getEpochSecond();
        verifier.verify(body, String.valueOf(now), sign(now, body));
        verifier.verify(body, String.valueOf(now - 300), sign(now - 300, body));
        verifier.verify(body, String.valueOf(now + 300), sign(now + 300, body));
        assertThrows(WebhookAuthenticationException.class,
                () -> verifier.verify(body, String.valueOf(now - 301),
                        sign(now - 301, body)));
        assertThrows(WebhookAuthenticationException.class,
                () -> verifier.verify(body, String.valueOf(now + 301),
                        sign(now + 301, body)));
    }

    @Test
    void rejectsTamperedPayloadTimestampOrSignature() throws Exception {
        var verifier = new SignedSandboxWebhookVerifier(SECRET, CLOCK);
        byte[] body = "{\"eventId\":\"event-0001\"}".getBytes(StandardCharsets.UTF_8);
        long epoch = CLOCK.instant().getEpochSecond();
        String signature = sign(epoch, body);
        assertThrows(WebhookAuthenticationException.class,
                () -> verifier.verify("{\"eventId\":\"event-0002\"}"
                        .getBytes(StandardCharsets.UTF_8), String.valueOf(epoch), signature));
        assertThrows(WebhookAuthenticationException.class,
                () -> verifier.verify(body, String.valueOf(epoch + 1), signature));
        assertThrows(WebhookAuthenticationException.class,
                () -> verifier.verify(body, String.valueOf(epoch), "v1-" + signature));
        assertThrows(WebhookAuthenticationException.class,
                () -> verifier.verify(body, String.valueOf(epoch), "v1=" + "0".repeat(64)));
        assertThrows(WebhookAuthenticationException.class,
                () -> verifier.verify(body, null, signature));
        assertThrows(WebhookAuthenticationException.class,
                () -> verifier.verify(body, "not-a-timestamp", signature));
        assertThrows(WebhookAuthenticationException.class,
                () -> verifier.verify(body, String.valueOf(epoch), null));
    }

    @Test
    void rejectsOversizedBodyAndWeakOrMissingSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new SignedSandboxWebhookVerifier("", CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new SignedSandboxWebhookVerifier(null, CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new SignedSandboxWebhookVerifier("weak-secret", CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new SignedSandboxWebhookVerifier(SECRET + "\n", CLOCK));
        var verifier = new SignedSandboxWebhookVerifier(SECRET, CLOCK);
        long now = CLOCK.instant().getEpochSecond();
        assertThrows(InvalidWebhookEventException.class,
                () -> verifier.verify(new byte[0], String.valueOf(now), "v1="+"0".repeat(64)));
        assertThrows(InvalidWebhookEventException.class,
                () -> verifier.verify(new byte[SignedSandboxWebhookVerifier.MAX_BODY_BYTES + 1],
                        String.valueOf(now), "v1="+"0".repeat(64)));
    }

    @Test
    void parserRejectsUnknownFieldsAndFalseOutcomes() {
        String ref = "SBX-1234567890ABCDEF12345678";
        String valid = """
                {"eventId":"event-0001","providerReference":"%s",
                 "orderId":7,"totalCrc":2500.00,"outcome":"APPROVED"}
                """.formatted(ref);
        var parsed = SandboxWebhookService.parse(valid.getBytes(StandardCharsets.UTF_8));
        assertEquals(7, parsed.orderId());
        assertEquals("APPROVED", parsed.outcome().name());
        for (String invalid : new String[] {
                valid.replace("APPROVED", "CAPTURED"),
                valid.replace("2500.00", "-1.00"),
                valid.replace("2500.00", "2500.001"),
                valid.replace("event-0001", "x"),
                valid.replace("SBX-", "EXT-"),
                valid.replace("}", ",\"card\":\"4111111111111111\"}"),
                "[]", "{", ""
        }) {
            assertThrows(InvalidWebhookEventException.class,
                    () -> SandboxWebhookService.parse(
                            invalid.getBytes(StandardCharsets.UTF_8)), invalid);
        }
        assertThrows(InvalidWebhookEventException.class, () ->
                SandboxWebhookService.parse(new byte[] {(byte)0xFF}));
    }

    private static String sign(long epoch, byte[] raw) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(Long.toString(epoch).getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) '.');
        return "v1=" + HexFormat.of().formatHex(mac.doFinal(raw));
    }
}
