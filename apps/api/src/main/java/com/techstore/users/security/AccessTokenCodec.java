package com.techstore.users.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Bearer opaco: 32 bytes aleatorios (256 bits), Base64 URL-safe.
 * Solo el SHA-256 del token se conserva en la base de datos.
 */
@Component
public final class AccessTokenCodec {

    private final SecureRandom random = new SecureRandom();

    public String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isWellFormed(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    public String hash(String token) {
        if (!isWellFormed(token)) {
            throw new IllegalArgumentException("Token inválido");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }
}
