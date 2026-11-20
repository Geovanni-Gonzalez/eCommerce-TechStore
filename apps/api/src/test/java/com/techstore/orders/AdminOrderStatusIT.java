package com.techstore.orders;

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

/** Validación HTTP de ROLE_ADMIN y auditoría V10 sobre PostgreSQL real. */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class AdminOrderStatusIT {
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
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void onlyAdminCanAccessStatusOperations() throws Exception {
        mvc.perform(get("/api/admin/orders/1/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(patch("/api/admin/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isUnauthorized());

        String client = userAndToken("CLIENTE");
        mvc.perform(get("/api/admin/orders/1/history")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(patch("/api/admin/orders/1/status")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCancellationWritesOneAuditableTransitionAndReturnsStock()
            throws Exception {
        String client = userAndToken("CLIENTE");
        String admin = userAndToken("ADMIN");
        long product = product();
        long orderId = createOrder(client, product);
        mvc.perform(get("/api/admin/orders/" + orderId + "/history")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].fromStatus").isEmpty())
                .andExpect(jsonPath("$[0].toStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].actorType").value("CLIENT"))
                .andExpect(jsonPath("$[0].reason").value("CREATED"))
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        long adminId = jdbc.queryForObject("""
                SELECT id FROM users WHERE role='ADMIN' AND
                    email LIKE 'hist.%@techstore.test' ORDER BY id DESC LIMIT 1
                """, Long.class);
        mvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertEquals("CANCELLED", orderStatus(orderId));
        assertEquals(5, stock(product));
        mvc.perform(get("/api/admin/orders/" + orderId + "/history")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$[1].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$[1].actorType").value("ADMIN"))
                .andExpect(jsonPath("$[1].actorId").isNumber())
                .andExpect(jsonPath("$[1].reason").value("ADMIN_CANCEL"));
        assertEquals(2, historyCount(orderId));
        assertEquals(adminId, jdbc.queryForObject("""
                SELECT actor_id FROM order_status_history
                WHERE order_id=? AND reason='ADMIN_CANCEL'
                """, Long.class, orderId));
    }

    @Test
    void invalidRequestedStatesAndBodiesLeaveOrderUntouched() throws Exception {
        String client = userAndToken("CLIENTE");
        String admin = userAndToken("ADMIN");
        long product = product();
        long orderId = createOrder(client, product);

        for (String body : new String[] {
                "{\"status\":\"PENDING\"}",
                "{\"status\":\"CONFIRMED\"}"
        }) {
            mvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ORDER_TRANSITION_NOT_ALLOWED"));
        }
        for (String body : new String[] {
                "{}", "{\"status\":\"SHIPPED\"}",
                "{\"status\":\"CANCELLED\",\"actorId\":999}", "{"
        }) {
            mvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_REQUEST"));
        }
        assertEquals("PENDING", orderStatus(orderId));
        assertEquals(3, stock(product));
        assertEquals(1, historyCount(orderId));
    }

    @Test
    void missingOrderAndAlreadyCancelledRejectTransitions() throws Exception {
        String client = userAndToken("CLIENTE");
        String admin = userAndToken("ADMIN");
        long product = product();
        long orderId = createOrder(client, product);
        mvc.perform(get("/api/admin/orders/999999999/history")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/orders/999999999/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_TRANSITION_NOT_ALLOWED"));
        assertEquals(2, historyCount(orderId));
        assertEquals(5, stock(product));
    }

    private long createOrder(String token, long product) throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":2}"))
                .andExpect(status().isOk());
        MvcResult result = mvc.perform(post("/api/orders")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingAddress\":" + ADDRESS + "}"))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long product() {
        return jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Mouse de auditoría', 1200.00, 5, TRUE) RETURNING id
                """, Long.class, "HISTORY-" + UUID.randomUUID());
    }

    private String userAndToken(String role) throws Exception {
        return userAndToken(role, "hist." + UUID.randomUUID() + "@techstore.test");
    }

    private String userAndToken(String role, String email) throws Exception {
        jdbc.update("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES(?,?,?,?)
                """, "Prueba historial", email, passwords.hash(PASSWORD).value(), role);
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\""
                        + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(r.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject(
                "SELECT status FROM orders WHERE id=?", String.class, orderId);
    }

    private int historyCount(long orderId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_status_history WHERE order_id=?",
                Integer.class, orderId);
    }

    private int stock(long productId) {
        return jdbc.queryForObject(
                "SELECT stock FROM products WHERE id=?", Integer.class, productId);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
