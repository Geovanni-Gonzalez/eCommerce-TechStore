package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.techstore.users.security.PasswordHasher;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
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
 * Pruebas HTTP con Spring Security real y PostgreSQL/Flyway V1-V6.
 * Ninguna escritura se realiza fuera de la base techstore_test.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class CheckoutQuoteIT {

    private static final String PASSWORD = "ClaveDePrueba2026";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHasher hasher;
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
    void unauthenticatedAndAdminUsersCannotQuote() throws Exception {
        mvc.perform(get("/api/checkout/quote"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        String admin = login(createUser("it.quote.admin@techstore.test", "ADMIN"));
        mvc.perform(get("/api/checkout/quote")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void emptyCartReturnsSafe422() throws Exception {
        String client = login(createUser("it.quote.empty@techstore.test", "CLIENTE"));
        mvc.perform(get("/api/checkout/quote")
                        .header("Authorization", bearer(client)))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType("application/problem+json")))
                .andExpect(jsonPath("$.code").value("EMPTY_CART"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void quotesTaxAndFlatShippingBelowThreshold() throws Exception {
        String email = "it.quote.below@techstore.test";
        String client = login(createUser(email, "CLIENTE"));
        long product = createProduct("QUOTE-BELOW", "Cable USB", "1250.25", 10);
        add(client, product, 2);

        mvc.perform(get("/api/checkout/quote")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.shippingPolicy").value("FREE_OVER_THRESHOLD"))
                .andExpect(jsonPath("$.estimateOnly").value(true))
                .andExpect(jsonPath("$.totals.subtotalCrc").value(2500.50))
                .andExpect(jsonPath("$.totals.shippingCrc").value(2500.00))
                .andExpect(jsonPath("$.totals.taxCrc").value(325.07))
                .andExpect(jsonPath("$.totals.totalCrc").value(5325.57));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    void quotesFreeShippingAtThreshold() throws Exception {
        String email = "it.quote.threshold@techstore.test";
        String client = login(createUser(email, "CLIENTE"));
        long product = createProduct("QUOTE-FREE", "Teclado", "15000.00", 10);
        add(client, product, 2);
        mvc.perform(get("/api/checkout/quote")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.subtotalCrc").value(30000))
                .andExpect(jsonPath("$.totals.shippingCrc").value(0))
                .andExpect(jsonPath("$.totals.taxCrc").value(3900))
                .andExpect(jsonPath("$.totals.totalCrc").value(33900));
    }

    @Test
    void quoteReReadsPriceFromPostgresAndNeverAcceptsClientTotals() throws Exception {
        String email = "it.quote.price@techstore.test";
        String client = login(createUser(email, "CLIENTE"));
        long product = createProduct("QUOTE-PRICE", "Mouse", "1000.00", 9);
        add(client, product, 2);
        jdbc.update("UPDATE products SET price_crc = ? WHERE id = ?",
                new BigDecimal("2000.00"), product);
        mvc.perform(get("/api/checkout/quote?totalCrc=1&userId=9999&shippingPolicy=FIXED")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.subtotalCrc").value(4000))
                .andExpect(jsonPath("$.totals.shippingCrc").value(2500))
                .andExpect(jsonPath("$.totals.taxCrc").value(520))
                .andExpect(jsonPath("$.totals.totalCrc").value(7020))
                .andExpect(jsonPath("$.shippingPolicy").value("FREE_OVER_THRESHOLD"));
    }

    @Test
    void insufficientStockAndInactiveProductsCannotBeQuoted() throws Exception {
        String client = login(createUser(
                "it.quote.stock@techstore.test", "CLIENTE"));
        long product = createProduct("QUOTE-STOCK", "Audífonos", "5000.00", 5);
        add(client, product, 3);
        jdbc.update("UPDATE products SET stock=2 WHERE id=?", product);
        mvc.perform(get("/api/checkout/quote")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_UNAVAILABLE"));
        jdbc.update("UPDATE products SET stock=9, active=FALSE WHERE id=?", product);
        mvc.perform(get("/api/checkout/quote")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_UNAVAILABLE"));
    }

    @Test
    void otherClientsCartCannotBeQuotedAndLogoutRevokesAccess() throws Exception {
        String alice = login(createUser(
                "it.quote.alice@techstore.test", "CLIENTE"));
        String bob = login(createUser(
                "it.quote.bob@techstore.test", "CLIENTE"));
        long product = createProduct("QUOTE-OWNER", "Monitor", "12500.00", 8);
        add(alice, product, 1);
        mvc.perform(get("/api/checkout/quote")
                        .header("Authorization", bearer(bob)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
        mvc.perform(get("/api/checkout/quote")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/checkout/quote")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isUnauthorized());
    }

    private String createUser(String email, String role) {
        jdbc.update("""
                INSERT INTO users (display_name,email,password_hash,role)
                VALUES (?,?,?,?)
                """, "Cliente cotización", email, hasher.hash(PASSWORD).value(), role);
        return email;
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long createProduct(
            String sku, String name, String price, int stock) {
        return jdbc.queryForObject("""
                INSERT INTO products (sku, name, price_crc, stock, active)
                VALUES (?, ?, ?, ?, TRUE) RETURNING id
                """, Long.class, sku, name, new BigDecimal(price), stock);
    }

    private void add(String token, long product, int quantity) throws Exception {
        mvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + product
                                + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
