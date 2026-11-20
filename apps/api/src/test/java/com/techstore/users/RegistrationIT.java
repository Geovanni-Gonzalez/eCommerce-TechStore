package com.techstore.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techstore.users.domain.PasswordHash;
import com.techstore.users.security.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end de Spring MVC + servicio + BCrypt real + JDBC + Flyway V3
 * sobre la base de pruebas aislada. Cada método termina en rollback.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class RegistrationIT {

    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordHasher hasher;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void assertTestDatabaseBeforeWrites() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        jdbc.update("DELETE FROM users WHERE email LIKE 'it.registration.%@techstore.test'");
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void registersClientAndStoresOnlyBcryptInPostgres() throws Exception {
        String password = "ClaveDePrueba2026";
        String body = """
                {
                  "displayName": "  Ana de prueba  ",
                  "email": " IT.REGISTRATION.OK@TECHSTORE.TEST ",
                  "password": "ClaveDePrueba2026"
                }
                """;

        var result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.displayName").value("Ana de prueba"))
                .andExpect(jsonPath("$.email").value("it.registration.ok@techstore.test"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(password));
        var row = jdbc.queryForMap("""
                SELECT display_name, role, password_hash, active
                FROM users WHERE email = ?
                """, "it.registration.ok@techstore.test");
        assertEquals("Ana de prueba", row.get("display_name"));
        assertEquals("CLIENTE", row.get("role"));
        assertEquals(true, row.get("active"));
        String hash = (String) row.get("password_hash");
        assertNotNull(hash);
        assertNotEquals(password, hash);
        assertTrue(hasher.matches(password, new PasswordHash(hash)));
    }

    @Test
    void duplicateEmailReturns409AndDoesNotCreateSecondAccount() throws Exception {
        String payload = """
                {"displayName":"Ana","email":"it.registration.dup@techstore.test",
                 "password":"ClaveDePrueba2026"}
                """;

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Otra Ana",
                                 "email":"IT.REGISTRATION.DUP@TECHSTORE.TEST",
                                 "password":"OtraClaveDePrueba2026"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void missingAndInvalidFieldsReturn400WithoutExposingPassword() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"A","email":"invalido","password":"corta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.password").doesNotExist());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ana","email":"it.registration.missing@techstore.test"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void cannotRegisterAdminOrSmuggleUnknownProperties() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Admin falso",
                                 "email":"it.registration.admin@techstore.test",
                                 "password":"ClaveDePrueba2026",
                                 "role":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Long.class, "it.registration.admin@techstore.test");
        assertEquals(0L, count);
    }

    @Test
    void malformedJsonReturnsSafe400() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
