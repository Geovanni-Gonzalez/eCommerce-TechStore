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
import com.techstore.users.dto.LoginRequest;
import com.techstore.users.repository.SessionTokenRepository;
import com.techstore.users.repository.UserAccountRepository;
import com.techstore.users.security.AccessTokenCodec;
import com.techstore.users.security.PasswordHasher;
import com.techstore.users.service.AuthenticationService;
import com.techstore.users.service.InvalidCredentialsException;
import com.techstore.users.service.LoginValidationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {

    private static final String HASH = "$2a$12$" + "A".repeat(53);

    @Test
    void validClientReceivesOpaqueTokenAndPublicContract() {
        var repo = new FakeAccounts(true);
        var sessions = new FakeSessions();
        var codec = new AccessTokenCodec();
        var service = new AuthenticationService(repo, sessions, new FakeHasher(), codec);

        var response = service.login(new LoginRequest(
                "  CLIENT@EXAMPLE.COM  ", "ClaveSegura2026"));

        assertEquals("Bearer", response.tokenType());
        assertTrue(codec.isWellFormed(response.accessToken()));
        assertEquals("CLIENT", response.user().role());
        assertEquals("client@example.com", response.user().email());
        assertEquals(1, sessions.tokens.size());
        assertFalse(sessions.tokens.containsKey(response.accessToken()));
        assertTrue(sessions.tokens.containsKey(codec.hash(response.accessToken())));
        assertTrue(sessions.expiresAt.isAfter(Instant.now().plusSeconds(7 * 3600)));
        assertFalse(response.user().toString().contains(HASH));
    }

    @Test
    void eachLoginGeneratesIndependentRandomToken() {
        var sessions = new FakeSessions();
        var service = new AuthenticationService(
                new FakeAccounts(true), sessions, new FakeHasher(), new AccessTokenCodec());
        String first = service.login(new LoginRequest(
                "client@example.com", "ClaveSegura2026")).accessToken();
        String second = service.login(new LoginRequest(
                "client@example.com", "ClaveSegura2026")).accessToken();
        assertNotEquals(first, second);
        assertEquals(2, sessions.tokens.size());
    }

    @Test
    void wrongPasswordAndUnknownUserShareSameError() {
        var service = new AuthenticationService(
                new FakeAccounts(true), new FakeSessions(), new FakeHasher(),
                new AccessTokenCodec());

        var badPassword = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest(
                        "client@example.com", "ClaveErronea2026")));
        var badUser = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest(
                        "missing@example.com", "ClaveSegura2026")));
        assertEquals(badPassword.getMessage(), badUser.getMessage());
    }

    @Test
    void inactiveAccountCannotLogin() {
        var service = new AuthenticationService(
                new FakeAccounts(false), new FakeSessions(), new FakeHasher(),
                new AccessTokenCodec());
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest(
                        "client@example.com", "ClaveSegura2026")));
    }

    @Test
    void malformedInputsAreRejectedWithoutCredentialValuesInErrors() {
        var service = new AuthenticationService(
                new FakeAccounts(true), new FakeSessions(), new FakeHasher(),
                new AccessTokenCodec());
        var validation = assertThrows(LoginValidationException.class,
                () -> service.login(new LoginRequest("no-es-email", "pass")));
        assertTrue(validation.errors().containsKey("email"));
        assertFalse(validation.getMessage().contains("pass"));
        assertThrows(LoginValidationException.class,
                () -> service.login(new LoginRequest(
                        "client@example.com", "á".repeat(40))));
    }

    @Test
    void unknownJsonFieldsIncludingRoleAreRejected() {
        var payload = new HashMap<String, Object>();
        payload.put("email", "client@example.com");
        payload.put("password", "ClaveSegura2026");
        payload.put("role", "ADMIN");

        assertThrows(LoginValidationException.class,
                () -> LoginRequest.fromJsonMap(payload));
        payload.remove("role");
        assertEquals("client@example.com", LoginRequest.fromJsonMap(payload).email());
        assertEquals("LoginRequest[REDACTED]",
                LoginRequest.fromJsonMap(payload).toString());
    }

    @Test
    void logoutRevokesOnlySpecifiedSession() {
        var sessions = new FakeSessions();
        var codec = new AccessTokenCodec();
        var service = new AuthenticationService(
                new FakeAccounts(true), sessions, new FakeHasher(), codec);
        var first = service.login(
                new LoginRequest("client@example.com", "ClaveSegura2026"));
        var second = service.login(
                new LoginRequest("client@example.com", "ClaveSegura2026"));

        service.logout(first.accessToken());
        assertFalse(sessions.findActiveUserId(codec.hash(first.accessToken())).isPresent());
        assertTrue(sessions.findActiveUserId(codec.hash(second.accessToken())).isPresent());
    }

    @Test
    void tokenCodecHashesAndRejectsMalformedCredentials() {
        var codec = new AccessTokenCodec();
        var token = codec.newToken();
        assertTrue(codec.isWellFormed(token));
        assertTrue(codec.hash(token).matches("[0-9a-f]{64}"));
        assertNotEquals(token, codec.hash(token));
        assertFalse(codec.isWellFormed("not-a-real-token"));
        assertThrows(IllegalArgumentException.class, () -> codec.hash("abc"));
    }

    private static final class FakeHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(String rawPassword) {
            return new PasswordHash(HASH);
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash hash) {
            return "ClaveSegura2026".equals(rawPassword);
        }
    }

    private static final class FakeAccounts implements UserAccountRepository {
        private final boolean active;

        private FakeAccounts(boolean active) {
            this.active = active;
        }

        @Override
        public Optional<UserAccount> findByEmail(UserEmail email) {
            return "client@example.com".equals(email.value())
                    ? findById(1L) : Optional.empty();
        }

        @Override
        public Optional<UserAccount> findById(long id) {
            return id == 1 ? Optional.of(new UserAccount(
                    1L, "Cliente", new UserEmail("client@example.com"),
                    new PasswordHash(HASH), UserRole.CLIENTE, active,
                    null, null)) : Optional.empty();
        }

        @Override
        public UserAccount createClient(
                String displayName, UserEmail email, PasswordHash hash) {
            throw new UnsupportedOperationException("No se registran cuentas en login");
        }
    }

    private static final class FakeSessions implements SessionTokenRepository {
        private final Map<String, Long> tokens = new HashMap<>();
        private Instant expiresAt;

        @Override
        public void create(long userId, String hash, Instant expiry) {
            tokens.put(hash, userId);
            expiresAt = expiry;
        }

        @Override
        public Optional<Long> findActiveUserId(String hash) {
            return Optional.ofNullable(tokens.get(hash));
        }

        @Override
        public void revoke(String hash) {
            tokens.remove(hash);
        }
    }
}
