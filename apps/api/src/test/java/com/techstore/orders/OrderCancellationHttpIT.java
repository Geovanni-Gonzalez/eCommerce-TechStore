package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.techstore.users.security.PasswordHasher;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** HTTP con autenticación real; fixtures revertidos por la transacción de prueba. */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class OrderCancellationHttpIT {
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
    void anonymousAndAdminCannotCancel() throws Exception {
        mvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        String admin = userAndToken("it.cancel.admin@techstore.test", "ADMIN");
        mvc.perform(post("/api/orders/1/cancel")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void ownerCanCancelTwiceButOtherUsersReceive404() throws Exception {
        String alice = userAndToken("it.cancel.alice@techstore.test", "CLIENTE");
        String bob = userAndToken("it.cancel.bob@techstore.test", "CLIENTE");
        long productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES('CANCEL-IT-001','Mouse cancel',1500.00,4,TRUE) RETURNING id
                """, Long.class);
        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isOk());
        MvcResult created = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingAddress\":" + ADDRESS + "}"))
                .andExpect(status().isCreated()).andReturn();
        long id = JSON.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        mvc.perform(post("/api/orders/" + id + "/cancel")
                .header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/orders/" + id + "/cancel")
                    .header("Authorization", "Bearer " + alice))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        assertEquals("CANCELLED", jdbc.queryForObject(
                "SELECT status FROM orders WHERE id=?", String.class, id));
        assertEquals(4, jdbc.queryForObject(
                "SELECT stock FROM products WHERE id=?", Integer.class, productId));
    }

    @Test
    void confirmedOrderReturns409AndNoStockChange() throws Exception {
        String token = userAndToken("it.cancel.confirmed@techstore.test", "CLIENTE");
        long productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES('CANCEL-IT-002','Mouse confirmado',1500.00,4,TRUE) RETURNING id
                """, Long.class);
        mvc.perform(post("/api/cart/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":1}"))
                .andExpect(status().isOk());
        MvcResult created = mvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingAddress\":" + ADDRESS + "}"))
                .andExpect(status().isCreated()).andReturn();
        long id = JSON.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        jdbc.update("UPDATE orders SET status='CONFIRMED' WHERE id=?", id);
        mvc.perform(post("/api/orders/" + id + "/cancel")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        assertEquals(3, jdbc.queryForObject(
                "SELECT stock FROM products WHERE id=?", Integer.class, productId));
    }

    private String userAndToken(String email, String role) throws Exception {
        jdbc.update("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES(?,?,?,?)
                """, "Cliente", email, passwords.hash(PASSWORD).value(), role);
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\""
                        + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(r.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
