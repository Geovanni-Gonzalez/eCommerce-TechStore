package com.techstore.users.service;

import com.techstore.users.domain.PasswordHash;
import com.techstore.users.domain.UserAccount;
import com.techstore.users.domain.UserEmail;
import com.techstore.users.dto.RegisterRequest;
import com.techstore.users.dto.RegisteredUserResponse;
import com.techstore.users.repository.UserAccountRepository;
import com.techstore.users.security.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de registro; depende solo de las abstracciones del repositorio
 * y del hasher, no de Spring JDBC ni de BCrypt directamente (SOLID/DIP).
 */
@Service
public class RegistrationService {

    private final UserAccountRepository accounts;
    private final PasswordHasher hasher;

    public RegistrationService(UserAccountRepository accounts, PasswordHasher hasher) {
        this.accounts = Objects.requireNonNull(accounts);
        this.hasher = Objects.requireNonNull(hasher);
    }

    @Transactional
    public RegisteredUserResponse register(RegisterRequest request) {
        if (request == null) {
            throw new RegistrationValidationException(
                    Map.of("body", "El cuerpo JSON es obligatorio"));
        }

        Map<String, String> errors = new LinkedHashMap<>();
        String displayName = validateName(request.displayName(), errors);
        UserEmail email = validateEmail(request.email(), errors);
        validatePassword(request.password(), errors);
        if (!errors.isEmpty()) {
            throw new RegistrationValidationException(errors);
        }

        // Chequeo rápido para evitar hashing costoso en duplicados habituales;
        // el índice UNIQUE es la protección definitiva frente a carreras.
        if (accounts.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        PasswordHash passwordHash = hasher.hash(request.password());
        try {
            UserAccount saved = accounts.createClient(displayName, email, passwordHash);
            return RegisteredUserResponse.from(saved);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    private static String validateName(String raw, Map<String, String> errors) {
        if (raw == null) {
            errors.put("displayName", "El nombre visible es obligatorio");
            return null;
        }
        String name = raw.strip();
        if (name.length() < 2 || name.length() > 120
                || name.codePoints().anyMatch(Character::isISOControl)) {
            errors.put("displayName", "El nombre debe tener entre 2 y 120 caracteres válidos");
        }
        return name;
    }

    private static UserEmail validateEmail(String raw, Map<String, String> errors) {
        if (raw == null) {
            errors.put("email", "El correo electrónico es obligatorio");
            return null;
        }
        try {
            return new UserEmail(raw);
        } catch (IllegalArgumentException exception) {
            errors.put("email", "El correo electrónico no es válido");
            return null;
        }
    }

    private static void validatePassword(String raw, Map<String, String> errors) {
        // BCrypt procesa como máximo 72 bytes: no permitir truncamiento silencioso
        // que haría equivalentes dos contraseñas distintas.
        if (raw == null || raw.length() < 8 || raw.length() > 128
                || raw.getBytes(StandardCharsets.UTF_8).length > 72
                || raw.codePoints().anyMatch(Character::isISOControl)) {
            errors.put("password",
                    "La contraseña debe tener entre 8 y 128 caracteres, "
                    + "máximo 72 bytes UTF-8 y sin caracteres de control");
        }
    }
}
