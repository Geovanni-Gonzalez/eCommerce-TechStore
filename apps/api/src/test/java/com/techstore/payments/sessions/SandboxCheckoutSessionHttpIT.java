package com.techstore.payments.sessions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.techstore.users.security.PasswordHasher;
import jakarta.servlet.Filter;
import java.util.UUID;
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

/** HTTP con Bearer CLIENT en inicio y token de un uso para sandbox público. */
@SpringBootTest(properties={
        "techstore.payment.provider=SANDBOX",
        "techstore.payment.sandbox.enabled=true",
        "techstore.payment.sandbox.outcome=DECLINED"
})
@ActiveProfiles("integration")
@Transactional
class SandboxCheckoutSessionHttpIT {
    private static final String PASSWORD = "ClaveDePrueba2026";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ADDRESS = """
            {"province":"San José","canton":"Central","district":"Carmen",
             "details":"Casa frente al parque central",
             "recipientName":"Ana Pérez","phone":"8888-1111"}
            """;

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHasher passwords;
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        assertEquals("techstore_test", jdbc.queryForObject(
                "SELECT current_database()", String.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void anonymousAdminAndOtherClientCannotStartSomeoneElsesSession()
            throws Exception {
        String owner = userAndToken("CLIENTE");
        String other = userAndToken("CLIENTE");
        String admin = userAndToken("ADMIN");
        long orderId = createOrder(owner);
        String path = "/api/orders/" + orderId + "/payment-sessions";
        mvc.perform(post(path).header("Idempotency-Key", "key-login-001"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", bearer(admin))
                .header("Idempotency-Key", "key-login-001"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer(other))
                .header("Idempotency-Key", "key-login-001"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/99999999/payment-sessions")
                .header("Authorization", bearer(owner))
                .header("Idempotency-Key", "key-login-001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void correctClientStartsCrcOnlySessionAndRedirectsToLocalSandbox()
            throws Exception {
        String owner = userAndToken("CLIENTE");
        long orderId = createOrder(owner);
        String path = "/api/orders/" + orderId + "/payment-sessions";
        MvcResult created = mvc.perform(post(path)
                .header("Authorization", bearer(owner))
                .header("Idempotency-Key", "key-http-redirect-001"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.currency").value("CRC"))
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andReturn();
        var json = JSON.readTree(created.getResponse().getContentAsString());
        String redirect = json.get("redirectUrl").asText();
        assertTrue(redirect.startsWith(
                "http://localhost:3000/pagos/sandbox#session="));
        String token = redirect.substring(redirect.indexOf("#session=") + 9);
        assertEquals(43, token.length());
        assertFalse(created.getResponse().getContentAsString().contains(
                "key-http-redirect-001"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_attempts WHERE order_id=?",
                Integer.class, orderId));
        mvc.perform(post("/api/payments/sandbox/sessions/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("CRC"))
                .andExpect(jsonPath("$.orderId").value(orderId));
        mvc.perform(post("/api/payments/sandbox/sessions/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIMULATED_DECLINED"));
        mvc.perform(post("/api/payments/sandbox/sessions/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SANDBOX_SESSION_NOT_FOUND"));
        mvc.perform(post(path).header("Authorization", bearer(owner))
                .header("Idempotency-Key", "key-http-redirect-001"))
                .andExpect(status().isConflict());
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM orders WHERE id=?", String.class, orderId));
        assertEquals("RESERVED", jdbc.queryForObject(
                "SELECT status FROM stock_reservations WHERE order_id=? LIMIT 1",
                String.class, orderId));
    }

    @Test
    void clientCannotSetAmountCurrencyReturnUrlOrCard() throws Exception {
        String owner = userAndToken("CLIENTE");
        long orderId = createOrder(owner);
        String path = "/api/orders/" + orderId + "/payment-sessions";
        mvc.perform(post(path).header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization", bearer(owner))
                .header("Idempotency-Key", "key with spaces"))
                .andExpect(status().isBadRequest());
        for (String payload : new String[]{
                "{\"totalCrc\":0.01}", "{\"currency\":\"USD\"}",
                "{\"returnUrl\":\"https://evil.example\"}",
                "{\"card\":\"4111111111111111\"}"
        }) {
            mvc.perform(post(path).header("Authorization", bearer(owner))
                    .header("Idempotency-Key", "key-http-body-001")
                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/payments/sandbox/sessions/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"bad\",\"totalCrc\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/sandbox/sessions/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_attempts WHERE order_id=?",
                Integer.class, orderId));
    }

    private long createOrder(String token) throws Exception {
        long productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Checkout hosted', 1500.00, 5, TRUE) RETURNING id
                """, Long.class, "SESHTTP-" + UUID.randomUUID());
        mvc.perform(post("/api/cart/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":1}"))
                .andExpect(status().isOk());
        MvcResult created = mvc.perform(post("/api/orders")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingAddress\":" + ADDRESS + "}"))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private String userAndToken(String role) throws Exception {
        String email = "it.session.http." + UUID.randomUUID() + "@techstore.test";
        jdbc.update("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES(?,?,?,?)
                """, "Pago sandbox", email, passwords.hash(PASSWORD).value(), role);
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\""
                        + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
