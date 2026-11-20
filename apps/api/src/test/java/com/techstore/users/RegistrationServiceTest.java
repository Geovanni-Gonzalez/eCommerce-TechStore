package com.techstore.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.techstore.users.domain.PasswordHash;
import com.techstore.users.domain.UserAccount;
import com.techstore.users.domain.UserEmail;
import com.techstore.users.domain.UserRole;
import com.techstore.users.dto.RegisterRequest;
import com.techstore.users.dto.RegisteredUserResponse;
import com.techstore.users.repository.UserAccountRepository;
import com.techstore.users.security.PasswordHasher;
import com.techstore.users.service.EmailAlreadyRegisteredException;
import com.techstore.users.service.RegistrationService;
import com.techstore.users.service.RegistrationValidationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class RegistrationServiceTest {

    @Test
    void createsOnlyClientWithNormalizedEmailAndPublicRole() {
        var repo = new FakeAccounts();
        var hasher = new RecordingHasher();
        var service = new RegistrationService(repo, hasher);

        RegisteredUserResponse result = service.register(
                new RegisterRequest("  Ana Pérez  ", " ANA@EXAMPLE.COM ", "ClaveSegura2026"));

        assertEquals("Ana Pérez", result.displayName());
        assertEquals("ana@example.com", result.email());
        assertEquals("CLIENT", result.role());
        assertEquals(1L, result.id());
        assertEquals("ClaveSegura2026", hasher.lastRaw);
        assertEquals(UserRole.CLIENTE, repo.findById(1L).orElseThrow().role());
        assertNotEquals("ClaveSegura2026",
                repo.findById(1L).orElseThrow().passwordHash().value());
        assertFalse(result.toString().contains("ClaveSegura2026"));
    }

    @Test
    void preexistingEmailIsConflictWithoutHashingAgain() {
        var repo = new FakeAccounts();
        var hasher = new RecordingHasher();
        var service = new RegistrationService(repo, hasher);
        service.register(new RegisterRequest("Ana", "ANA@EXAMPLE.COM", "ClaveSegura2026"));
        int calls = hasher.calls;

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> service.register(new RegisterRequest(
                        "Ana", "ana@example.com", "OtraClave2026")));
        assertEquals(calls, hasher.calls);
    }

    @Test
    void uniqueViolationRaceIsAlsoConflict() {
        var repo = new FakeAccounts();
        repo.failInsert = true;
        var service = new RegistrationService(repo, new RecordingHasher());

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> service.register(new RegisterRequest(
                        "Ana", "ana@example.com", "ClaveSegura2026")));
    }

    @Test
    void invalidNameEmailAndPasswordAreRejectedBeforeHashing() {
        var hasher = new RecordingHasher();
        var service = new RegistrationService(new FakeAccounts(), hasher);

        RegistrationValidationException error = assertThrows(
                RegistrationValidationException.class,
                () -> service.register(new RegisterRequest(
                        "A", "correo-invalido", "corta")));

        assertEquals(3, error.errors().size());
        assertTrue(error.errors().containsKey("displayName"));
        assertTrue(error.errors().containsKey("email"));
        assertTrue(error.errors().containsKey("password"));
        assertEquals(0, hasher.calls);
        assertFalse(error.getMessage().contains("corta"));
    }

    @Test
    void bcryptByteLimitRejectsSilentTruncation() {
        var service = new RegistrationService(new FakeAccounts(), new RecordingHasher());

        String unicodePassword = "á".repeat(40); // 80 bytes UTF-8
        RegistrationValidationException error = assertThrows(
                RegistrationValidationException.class,
                () -> service.register(new RegisterRequest(
                        "Ana", "ana@example.com", unicodePassword)));

        assertTrue(error.errors().containsKey("password"));
    }

    @Test
    void rejectsPasswordWithControlCharacters() {
        var service = new RegistrationService(new FakeAccounts(), new RecordingHasher());

        assertThrows(RegistrationValidationException.class,
                () -> service.register(new RegisterRequest(
                        "Ana", "ana@example.com", "Clave\nSegura2026")));
    }

    @Test
    void rejectsUnknownFieldsIncludingRoleAndInvalidTypes() {
        var body = new HashMap<String, Object>();
        body.put("displayName", "Ana");
        body.put("email", "ana@example.com");
        body.put("password", "ClaveSegura2026");
        body.put("role", "ADMIN");

        RegistrationValidationException error = assertThrows(
                RegistrationValidationException.class,
                () -> RegisterRequest.fromJsonMap(body));
        assertTrue(error.errors().containsKey("body"));

        body.remove("role");
        body.put("password", 1234);
        assertThrows(RegistrationValidationException.class,
                () -> RegisterRequest.fromJsonMap(body));
    }

    @Test
    void neverPrintsPasswordFromRequest() {
        var request = new RegisterRequest("Ana", "ana@example.com", "SecretoTest2026");
        assertFalse(request.toString().contains("SecretoTest2026"));
        assertEquals("RegisterRequest[REDACTED]", request.toString());
    }

    @Test
    void adminInternalRoleMapsToPublicAdminButRegisterCannotRequestIt() {
        var account = new UserAccount(
                10L, "Administración", new UserEmail("admin@example.com"),
                new PasswordHash("$2a$12$" + "A".repeat(53)),
                UserRole.ADMIN, true, null, null);
        assertEquals("ADMIN", RegisteredUserResponse.from(account).role());
    }

    private static final class RecordingHasher implements PasswordHasher {
        private int calls;
        private String lastRaw;

        @Override
        public PasswordHash hash(String rawPassword) {
            calls++;
            lastRaw = rawPassword;
            return new PasswordHash("$2a$12$" + "A".repeat(53));
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash storedHash) {
            return false;
        }
    }

    private static final class FakeAccounts implements UserAccountRepository {
        private final Map<String, UserAccount> users = new HashMap<>();
        private boolean failInsert;

        @Override
        public Optional<UserAccount> findByEmail(UserEmail email) {
            return Optional.ofNullable(users.get(email.value()));
        }

        @Override
        public Optional<UserAccount> findById(long id) {
            return users.values().stream()
                    .filter(user -> user.id() == id)
                    .findFirst();
        }

        @Override
        public UserAccount createClient(
                String displayName, UserEmail email, PasswordHash passwordHash) {
            if (failInsert) {
                throw new DuplicateKeyException("ux_users_email");
            }
            UserAccount user = new UserAccount(
                    (long) users.size() + 1, displayName, email, passwordHash,
                    UserRole.CLIENTE, true, null, null);
            users.put(email.value(), user);
            return user;
        }
    }
}
