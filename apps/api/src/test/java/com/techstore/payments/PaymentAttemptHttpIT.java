package com.techstore.payments;

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

/** Autenticación real HTTP y aislamiento por propietario, sin cobros externos. */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class PaymentAttemptHttpIT {
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
    void setUp() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void anonymousAndAdminCannotAccessClientPayments() throws Exception {
        mvc.perform(post("/api/orders/1/payment-attempts")
                .header("Idempotency-Key", "attempt-http-001"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/1/payment-attempts/1"))
                .andExpect(status().isUnauthorized());
        String admin = userAndToken("ADMIN");
        mvc.perform(post("/api/orders/1/payment-attempts")
                .header("Authorization", bearer(admin))
                .header("Idempotency-Key", "attempt-http-001"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/1/payment-attempts/1")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCreatesAndReplaysSameAttemptWithoutPaymentOrKeyLeak() throws Exception {
        String owner = userAndToken("CLIENTE");
        String stranger = userAndToken("CLIENTE");
        long order = createOrder(owner);
        String url = "/api/orders/" + order + "/payment-attempts";
        MvcResult first = mvc.perform(post(url)
                .header("Authorization", bearer(owner))
                .header("Idempotency-Key", "attempt-http-replay-001"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.orderId").value(order))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.providerReference").isEmpty())
                .andReturn();
        var result = JSON.readTree(first.getResponse().getContentAsString());
        long id = result.get("id").asLong();
        assertFalse(first.getResponse().getContentAsString().contains("attempt-http-replay-001"));
        assertEquals("/api/orders/" + order + "/payment-attempts/" + id,
                first.getResponse().getHeader("Location"));
        mvc.perform(post(url)
                .header("Authorization", bearer(owner))
                .header("Idempotency-Key", "attempt-http-replay-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get(url + "/" + id)
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get(url + "/" + id)
                .header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_ATTEMPT_NOT_FOUND"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_attempts WHERE order_id=?
                """, Integer.class, order));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM orders WHERE id=?", String.class, order));
    }

    @Test
    void invalidPayloadAndMissingKeyAreRejectedAndDoNotCreateAttempt()
            throws Exception {
        String owner = userAndToken("CLIENTE");
        long order = createOrder(owner);
        String url = "/api/orders/" + order + "/payment-attempts";
        mvc.perform(post(url).header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_ATTEMPT"));
        mvc.perform(post(url).header("Authorization", bearer(owner))
                .header("Idempotency-Key", "bad key"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).header("Authorization", bearer(owner))
                .header("Idempotency-Key", "attempt-json-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"totalCrc\":1,\"card\":\"4111111111111111\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).header("Authorization", bearer(owner))
                .header("Idempotency-Key", "attempt-empty-object")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_attempts WHERE order_id=?",
                Integer.class, order));
    }

    @Test
    void foreignOrNonexistentOrderCannotBeProbed() throws Exception {
        String owner = userAndToken("CLIENTE");
        String other = userAndToken("CLIENTE");
        long order = createOrder(owner);
        mvc.perform(post("/api/orders/" + order + "/payment-attempts")
                .header("Authorization", bearer(other))
                .header("Idempotency-Key", "attempt-other-user-001"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/999999999/payment-attempts")
                .header("Authorization", bearer(owner))
                .header("Idempotency-Key", "attempt-nonexistent-001"))
                .andExpect(status().isNotFound());
    }

    private long createOrder(String token) throws Exception {
        long product = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Pago HTTP', 1500.00, 5, TRUE) RETURNING id
                """, Long.class, "PAYHTTP-" + UUID.randomUUID());
        mvc.perform(post("/api/cart/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":1}"))
                .andExpect(status().isOk());
        MvcResult result = mvc.perform(post("/api/orders")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingAddress\":" + ADDRESS + "}"))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private String userAndToken(String role) throws Exception {
        String email = "it.payment.http." + UUID.randomUUID() + "@techstore.test";
        jdbc.update("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES(?,?,?,?)
                """, "Pago HTTP", email, passwords.hash(PASSWORD).value(), role);
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
