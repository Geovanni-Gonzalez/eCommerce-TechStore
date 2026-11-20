package com.techstore.cart;

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
 * Pruebas HTTP con Spring Security REAL, Flyway V1-V5 y PostgreSQL
 * exclusivamente en techstore_test; todos los cambios se revierten.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class CartIT {
    private static final String PASSWORD = "ClaveDePrueba2026";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHasher passwords;
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class),
                "Jamás escribir pruebas en una base que no sea techstore_test");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void anonymousAndAdminCannotUseClientCart() throws Exception {
        mvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        String admin = createAndLogin("it.cart.admin@techstore.test", "ADMIN");
        mvc.perform(get("/api/cart").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void clientStartsWithEmptyCartAndReadsItAgain() throws Exception {
        String token = createAndLogin("it.cart.a@techstore.test", "CLIENTE");
        mvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.subtotalCrc").value(0))
                .andExpect(jsonPath("$.checkoutReady").value(false));
    }

    @Test
    void addIncrementAndPatchUseDatabasePrices() throws Exception {
        String token = createAndLogin("it.cart.a@techstore.test", "CLIENTE");
        long product = createProduct("CART-ADD", "Mouse", "1250.25", 15, true);
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + product + ",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.subtotalCrc").value(2500.50))
                .andExpect(jsonPath("$.checkoutReady").value(true));
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + product + ",\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.subtotalCrc").value(6251.25));
        mvc.perform(patch("/api/cart/items/" + product)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(4))
                .andExpect(jsonPath("$.subtotalCrc").value(5001.00));
        assertEquals(4, jdbc.queryForObject(
                "SELECT ci.quantity FROM cart_items ci JOIN carts c ON c.id=ci.cart_id "
                + "WHERE c.user_id=(SELECT id FROM users WHERE email=?)",
                Integer.class, "it.cart.a@techstore.test"));
    }

    @Test
    void twoUsersCannotReadEditOrDeleteEachOthersItems() throws Exception {
        String a = createAndLogin("it.cart.a@techstore.test", "CLIENTE");
        String b = createAndLogin("it.cart.b@techstore.test", "CLIENTE");
        long product = createProduct("CART-OWNER", "Teclado", "9900.00", 20, true);
        add(a, product, 2).andExpect(status().isOk());
        mvc.perform(get("/api/cart").header("Authorization", bearer(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(patch("/api/cart/items/" + product)
                        .header("Authorization", bearer(b))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/cart/items/" + product)
                        .header("Authorization", bearer(b)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").header("Authorization", bearer(a)))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void invalidInputAndInjectedPriceOrUserAreRejected() throws Exception {
        String token = createAndLogin("it.cart.a@techstore.test", "CLIENTE");
        long product = createProduct("CART-VALID", "Mouse", "100.00", 5, true);
        for (String body : new String[]{
                "{\"productId\":" + product + ",\"quantity\":0}",
                "{\"productId\":" + product + ",\"quantity\":1.5}",
                "{\"productId\":" + product + ",\"quantity\":100}",
                "{\"productId\":" + product + ",\"quantity\":1,\"userId\":2}",
                "{\"productId\":" + product + ",\"quantity\":1,\"priceCrc\":0}",
                "{\"quantity\":1}",
                "{\"productId\":\"no-es-int\",\"quantity\":1}"
        }) {
            mvc.perform(post("/api/cart/items").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class));
    }

    @Test
    void insufficientStockAndInactiveProductsAreHandled() throws Exception {
        String token = createAndLogin("it.cart.a@techstore.test", "CLIENTE");
        long product = createProduct("CART-STOCK", "Audífonos", "2500.00", 4, true);
        add(token, product, 5).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_CONFLICT"));
        add(token, product, 3).andExpect(status().isOk());
        add(token, product, 2).andExpect(status().isConflict());
        jdbc.update("UPDATE products SET active=FALSE WHERE id=?", product);
        add(token, product, 1).andExpect(status().isNotFound());
        mvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.items[0].available").value(false))
                .andExpect(jsonPath("$.checkoutReady").value(false));
        mvc.perform(delete("/api/cart/items/" + product)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void pricesAreReadAgainAndAvailabilityChangesWithStock() throws Exception {
        String token = createAndLogin("it.cart.a@techstore.test", "CLIENTE");
        long product = createProduct("CART-PRICE", "Cable", "500.00", 8, true);
        add(token, product, 4).andExpect(status().isOk());
        jdbc.update("UPDATE products SET price_crc=750.00, stock=2 WHERE id=?", product);
        mvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.subtotalCrc").value(3000))
                .andExpect(jsonPath("$.items[0].stock").value(2))
                .andExpect(jsonPath("$.items[0].available").value(false))
                .andExpect(jsonPath("$.checkoutReady").value(false));
        mvc.perform(patch("/api/cart/items/" + product)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutReady").value(true))
                .andExpect(jsonPath("$.subtotalCrc").value(1500));
    }

    @Test
    void removeAndClearAreIdempotent() throws Exception {
        String token = createAndLogin("it.cart.a@techstore.test", "CLIENTE");
        long product = createProduct("CART-CLEAR", "Cable", "600.00", 10, true);
        add(token, product, 1).andExpect(status().isOk());
        mvc.perform(delete("/api/cart/items/" + product)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/cart/items/" + product)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        add(token, product, 2).andExpect(status().isOk());
        mvc.perform(delete("/api/cart").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/cart").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions add(
            String token, long product, int quantity) throws Exception {
        return mvc.perform(post("/api/cart/items").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":" + quantity + "}"));
    }

    private String createAndLogin(String email, String role) throws Exception {
        jdbc.update("""
                INSERT INTO users (display_name,email,password_hash,role)
                VALUES (?,?,?,?)
                """, "Cliente de prueba", email, passwords.hash(PASSWORD).value(), role);
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long createProduct(
            String sku, String name, String price, int stock, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO products (sku,name,price_crc,stock,active)
                VALUES (?,?,?,?,?) RETURNING id
                """, Long.class, sku, name, new BigDecimal(price), stock, active);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
