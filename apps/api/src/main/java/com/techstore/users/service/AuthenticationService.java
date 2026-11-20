package com.techstore.users.service;

import com.techstore.users.domain.UserAccount;
import com.techstore.users.domain.UserEmail;
import com.techstore.users.dto.AuthSessionResponse;
import com.techstore.users.dto.LoginRequest;
import com.techstore.users.dto.RegisteredUserResponse;
import com.techstore.users.repository.SessionTokenRepository;
import com.techstore.users.repository.UserAccountRepository;
import com.techstore.users.security.AccessTokenCodec;
import com.techstore.users.security.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordina autenticación/revocación; la seguridad HTTP vive en otro adaptador.
 * No expone si falló el email, el password o el estado de la cuenta.
 */
@Service
public class AuthenticationService {

    public static final Duration TOKEN_LIFETIME = Duration.ofHours(8);

    private final UserAccountRepository accounts;
    private final SessionTokenRepository sessions;
    private final PasswordHasher hasher;
    private final AccessTokenCodec tokens;

    public AuthenticationService(
            UserAccountRepository accounts, SessionTokenRepository sessions,
            PasswordHasher hasher, AccessTokenCodec tokens) {
        this.accounts = Objects.requireNonNull(accounts);
        this.sessions = Objects.requireNonNull(sessions);
        this.hasher = Objects.requireNonNull(hasher);
        this.tokens = Objects.requireNonNull(tokens);
    }

    @Transactional
    public AuthSessionResponse login(LoginRequest request) {
        if (request == null) {
            throw new LoginValidationException(Map.of("body", "Se requiere un objeto JSON"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        UserEmail email = null;
        try {
            email = new UserEmail(request.email());
        } catch (IllegalArgumentException | NullPointerException ex) {
            errors.put("email", "El correo no es válido");
        }
        String password = request.password();
        if (password == null || password.isBlank() || password.length() > 128
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            errors.put("password", "La contraseña no tiene un formato válido");
        }
        if (!errors.isEmpty()) {
            throw new LoginValidationException(errors);
        }

        UserAccount user = accounts.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!hasher.matches(password, user.passwordHash()) || !user.active()) {
            throw new InvalidCredentialsException();
        }

        String token = tokens.newToken();
        sessions.create(user.id(), tokens.hash(token), Instant.now().plus(TOKEN_LIFETIME));
        return new AuthSessionResponse(token, "Bearer", RegisteredUserResponse.from(user));
    }

    @Transactional
    public void logout(String rawToken) {
        sessions.revoke(tokens.hash(rawToken));
    }
}
