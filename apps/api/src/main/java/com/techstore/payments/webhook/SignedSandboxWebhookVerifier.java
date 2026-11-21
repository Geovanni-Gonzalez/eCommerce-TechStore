package com.techstore.payments.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifica HMAC-SHA256 sobre los bytes exactos: ASCII(timestamp) + "." + rawBody.
 * No utiliza JSON reserializado, ni comparación de firma con String.equals.
 */
public final class SignedSandboxWebhookVerifier {
    public static final int MAX_BODY_BYTES = 16 * 1024;
    public static final long MAX_SKEW_SECONDS = 300;
    private final byte[] key;
    private final Clock clock;

    public SignedSandboxWebhookVerifier(String secret, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.indexOf('\n') >= 0 || secret.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "Webhook HMAC requiere un secreto externo de 32 bytes o más");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8).clone();
        this.clock = Objects.requireNonNull(clock);
    }

    public void verify(byte[] body, String timestamp, String signature) {
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw new InvalidWebhookEventException();
        }
        if (timestamp == null || !timestamp.matches("[0-9]{10,11}")
                || signature == null || !signature.matches("v1=[0-9a-f]{64}")) {
            throw new WebhookAuthenticationException();
        }
        final long supplied;
        try {
            supplied = Long.parseLong(timestamp);
        } catch (NumberFormatException invalid) {
            throw new WebhookAuthenticationException();
        }
        long now = clock.instant().getEpochSecond();
        if (supplied < now - MAX_SKEW_SECONDS
                || supplied > now + MAX_SKEW_SECONDS) {
            throw new WebhookAuthenticationException();
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            byte[] expected = mac.doFinal(body);
            byte[] actual = HexFormat.of().parseHex(signature.substring(3));
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new WebhookAuthenticationException();
            }
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 no disponible", impossible);
        }
    }
}
