package com.techstore.payments.sessions;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

/**
 * Solo origen frontend explícitamente permitido; sin returnUrl de navegador.
 * En producción HTTPS; HTTP limitado a loopback para demo local.
 */
public final class SandboxCheckoutRedirectPolicy {
    private final String origin;

    public SandboxCheckoutRedirectPolicy(String redirectOrigin, String allowedOrigins) {
        URI uri;
        try {
            uri = URI.create(Objects.requireNonNull(redirectOrigin));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IllegalArgumentException("Origen sandbox inválido", invalid);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getRawPath() == null || uri.getRawPath().isEmpty()
                        || uri.getRawPath().equals("/"))
                || !(scheme.equals("https") || (scheme.equals("http")
                    && (host.equals("localhost") || host.equals("127.0.0.1"))))) {
            throw new IllegalArgumentException("Redirección sandbox no segura");
        }
        String normalized = uri.toASCIIString().replaceAll("/$", "");
        boolean listed = Arrays.stream(
                        Objects.requireNonNull(allowedOrigins).split(","))
                .map(String::strip)
                .map(value -> value.replaceAll("/$", ""))
                .anyMatch(normalized::equals);
        if (!listed) {
            throw new IllegalArgumentException(
                    "El origen de redirección no está permitido en CORS");
        }
        origin = normalized;
    }

    public String redirectUrl(String token) {
        SandboxSessionToken.hash(token); // formato y longitud validado
        // Token de 256 bits en fragmento: no se envía al servidor en la URL.
        return origin + "/pagos/sandbox#session=" + token;
    }
}
