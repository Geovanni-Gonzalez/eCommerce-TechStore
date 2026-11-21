package com.techstore.payments.sessions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Token aleatorio de 256 bits. Solo SHA-256 del token viaja a la BD. */
public final class SandboxSessionToken {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SandboxSessionToken() { }

    public static String generate() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new SandboxSessionUnavailableException();
        }
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 no disponible", failure);
        }
    }
}
