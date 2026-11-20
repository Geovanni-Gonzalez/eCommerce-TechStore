package com.techstore.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techstore.users.security.AccessTokenCodec;
import com.techstore.users.security.PasswordHasher;
import jakarta.servlet.Filter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP/seguridad reales con PostgreSQL + Flyway V4 sobre techstore_test.
 * Agrega el filtro Spring Security explícitamente a MockMvc (sin mockearlo).
 * Los datos y las sesiones se revierten al acabar cada test.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class AuthenticationIT {

    private static final String PASSWORD = "ClaveSeguraDePrueba2026";
    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordHasher hasher;
    @Autowired private AccessTokenCodec codec;
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void configureIsolatedDatabaseAndSecurityFilters() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class),
                "Nunca escribir sesiones fuera de la base aislada");
        jdbc.update("""
                DELETE FROM auth_sessions WHERE user_id IN
                (SELECT id FROM users WHERE email LIKE 'it.auth.%@techstore.test')
                """);
        jdbc.update("DELETE FROM users WHERE email LIKE 'it.auth.%@techstore.test'");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void loginStoresOnlyTokenHashAndReturnsPublicUser() throws Exception {
        long id = createUser("it.auth.client@techstore.test", "CLIENTE", true);
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("it.auth.client@techstore.test", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.id").value(id))
                .andExpect(jsonPath("$.user.role").value("CLIENT"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String rawToken = JSON.readTree(json).get("accessToken").asText();
        assertTrue(codec.isWellFormed(rawToken));
        assertFalse(json.contains("$2a$"));
        assertFalse(json.contains(PASSWORD));

        var row = jdbc.queryForMap("""
                SELECT user_id, token_hash, revoked_at, expires_at
                FROM auth_sessions WHERE user_id = ?
                """, id);
        assertEquals(id, ((Number) row.get("user_id")).longValue());
        assertEquals(codec.hash(rawToken), row.get("token_hash"));
        assertNotEquals(rawToken, row.get("token_hash"));
        assertNotNull(row.get("expires_at"));
        assertEquals(null, row.get("revoked_at"));

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("it.auth.client@techstore.test"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void logoutImmediatelyRevokesTheToken() throws Exception {
        createUser("it.auth.client@techstore.test", "CLIENTE", true);
        String token = login("it.auth.client@techstore.test", PASSWORD);

        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        assertNotNull(jdbc.queryForObject(
                "SELECT revoked_at FROM auth_sessions WHERE token_hash = ?",
                Object.class, codec.hash(token)));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privateRoutesRejectMissingMalformedAndExpiredTokens() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/admin/check"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer INVALID"))
                .andExpect(status().isUnauthorized());

        long id = createUser("it.auth.client@techstore.test", "CLIENTE", true);
        String token = login("it.auth.client@techstore.test", PASSWORD);
        jdbc.update("""
                UPDATE auth_sessions SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                WHERE user_id = ?
                """, id);

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotReadAdminButAdminCan() throws Exception {
        createUser("it.auth.client@techstore.test", "CLIENTE", true);
        createUser("it.auth.admin@techstore.test", "ADMIN", true);
        String clientToken = login("it.auth.client@techstore.test", PASSWORD);
        String adminToken = login("it.auth.admin@techstore.test", PASSWORD);

        mvc.perform(get("/api/admin/check")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/admin/check")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void accountDeactivationInvalidatesExistingSessions() throws Exception {
        long id = createUser("it.auth.client@techstore.test", "CLIENTE", true);
        String token = login("it.auth.client@techstore.test", PASSWORD);
        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", id);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("it.auth.client@techstore.test", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void wrongPasswordAndUnknownEmailUseIdenticalResponse() throws Exception {
        createUser("it.auth.client@techstore.test", "CLIENTE", true);
        var wrong = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("it.auth.client@techstore.test", "ClaveEquivocada")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();
        var unknown = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("it.auth.missing@techstore.test", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();
        assertEquals(wrong.getResponse().getContentAsString(),
                unknown.getResponse().getContentAsString());
    }

    @Test
    void malformedLoginAndRoleInjectionReturnBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"it.auth.client@techstore.test",
                                 "password":"ClaveSeguraDePrueba2026", "role":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void publicCatalogAndHealthStayOpenWithSecurityEnabled() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long createUser(String email, String role, boolean active) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (display_name, email, password_hash, role, active)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, "Usuario de prueba", email,
                hasher.hash(PASSWORD).value(), role, active);
        assertNotNull(id);
        return id;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
