package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.techstore.orders.inventory.CheckoutStockRepository;
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

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class CheckoutOrderIT {
    private static final String PASSWORD = "ClaveDePrueba2026";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ADDRESS = """
            {"province":"San José","canton":"Central","district":"Carmen",
             "details":"Frente a la plaza central, casa azul",
             "recipientName":"Ana Pérez","phone":"8888-1111"}
            """;
    private static final String BODY = "{\"shippingAddress\":" + ADDRESS + "}";

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHasher passwords;
    @Autowired WebApplicationContext context;
    @Autowired CheckoutStockRepository inventory;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class),
                "Nunca probar escrituras sobre la base de desarrollo");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void anonymousAndAdminCannotConfirmOrder() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        String admin = userAndToken("it.checkout.admin@techstore.test", "ADMIN");
        mvc.perform(post("/api/orders").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void confirmationCreatesPendingOrderAndReservationAtomically() throws Exception {
        String email = "it.checkout.success@techstore.test";
        String token = userAndToken(email, "CLIENTE");
        long product = product("IT-CHECKOUT-OK", "Cable USB", "1250.25", 5);
        add(token, product, 2);

        MvcResult result = confirm(token, BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentMode").value("SIMULATED"))
                .andExpect(jsonPath("$.items[0].name").value("Cable USB"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPriceCrc").value(1250.25))
                .andExpect(jsonPath("$.items[0].lineTotalCrc").value(2500.50))
                .andExpect(jsonPath("$.totals.shippingCrc").value(2500))
                .andExpect(jsonPath("$.totals.taxCrc").value(325.07))
                .andExpect(jsonPath("$.totals.totalCrc").value(5325.57))
                .andExpect(jsonPath("$.shippingAddress.province").value("San José"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andReturn();
        long orderId = JSON.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
        assertEquals("/api/orders/" + orderId,
                result.getResponse().getHeader("Location"));
        assertEquals(3, stock(product));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_reservations WHERE order_id=? "
                        + "AND product_id=? AND quantity=2 AND status='RESERVED'",
                Integer.class, orderId, product));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id=?",
                Integer.class, orderId));
        mvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.items.length()").value(0));
        confirm(token, BODY).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    void recalculatesChangedCatalogPriceBeforeSavingHistoricalSnapshot()
            throws Exception {
        String token = userAndToken("it.checkout.price@techstore.test", "CLIENTE");
        long product = product("IT-CHECKOUT-PRICE", "Mouse viejo", "500.00", 8);
        add(token, product, 2);
        jdbc.update("UPDATE products SET name=?, price_crc=? WHERE id=?",
                "Mouse nuevo", new BigDecimal("1000.00"), product);
        confirm(token, BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].name").value("Mouse nuevo"))
                .andExpect(jsonPath("$.items[0].unitPriceCrc").value(1000))
                .andExpect(jsonPath("$.totals.subtotalCrc").value(2000))
                .andExpect(jsonPath("$.totals.shippingCrc").value(2500))
                .andExpect(jsonPath("$.totals.taxCrc").value(260))
                .andExpect(jsonPath("$.totals.totalCrc").value(4760));
        assertEquals(6, stock(product));
    }

    @Test
    void rejectedAddressAndForgedPricesDoNotAffectCartOrStock()
            throws Exception {
        String token = userAndToken("it.checkout.invalid@techstore.test", "CLIENTE");
        long product = product("IT-CHECKOUT-INVALID", "Mouse", "2500.00", 4);
        add(token, product, 1);
        for (String body : new String[]{
                "{}",
                "{\"shippingAddress\":{},\"totalCrc\":1}",
                "{\"shippingAddress\":" + ADDRESS + ",\"userId\":999}",
                "{\"shippingAddress\":" + ADDRESS + ",\"items\":[]}",
                "{\"shippingAddress\":{" +
                        "\"province\":\"San José\",\"canton\":\"Carmen\"}}",
                "{\"shippingAddress\":null}",
                "{\"shippingAddress\":",
        }) {
            confirm(token, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertEquals(4, stock(product));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cart_items", Integer.class));
    }

    @Test
    void reducedStockAndInactiveProductAbortBeforeCreatingOrder()
            throws Exception {
        String token = userAndToken("it.checkout.stock@techstore.test", "CLIENTE");
        long product = product("IT-CHECKOUT-STOCK", "Auriculares", "2000.00", 5);
        add(token, product, 3);
        jdbc.update("UPDATE products SET stock = 2 WHERE id=?", product);
        confirm(token, BODY).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_UNAVAILABLE"));
        assertEquals(2, stock(product));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_reservations", Integer.class));
        jdbc.update("UPDATE products SET stock=9, active=FALSE WHERE id=?", product);
        confirm(token, BODY).andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    void cannotCreateOrderFromAnotherUsersCart() throws Exception {
        String alice = userAndToken("it.checkout.alice@techstore.test", "CLIENTE");
        String bob = userAndToken("it.checkout.bob@techstore.test", "CLIENTE");
        long product = product("IT-CHECKOUT-ISOLATE", "Teclado", "999.00", 3);
        add(alice, product, 1);
        confirm(bob, BODY).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
        assertEquals(3, stock(product));
        confirm(alice, BODY).andExpect(status().isCreated());
        assertEquals(2, stock(product));
    }

    @Test
    void releasingReservationsRestoresStockExactlyOnce() throws Exception {
        String token = userAndToken("it.checkout.release@techstore.test", "CLIENTE");
        long product = product("IT-CHECKOUT-RELEASE", "Cable", "1000.00", 6);
        add(token, product, 3);
        long orderId = orderId(confirm(token, BODY).andExpect(status().isCreated())
                .andReturn());
        assertEquals(3, stock(product));
        inventory.releaseReservations(orderId);
        inventory.releaseReservations(orderId);
        inventory.consumeReservations(orderId); // no efecto sobre RELEASED
        assertEquals(6, stock(product));
        assertEquals("RELEASED", jdbc.queryForObject(
                "SELECT status FROM stock_reservations WHERE order_id=?",
                String.class, orderId));
    }

    @Test
    void consumingReservationsDoesNotReturnStock() throws Exception {
        String token = userAndToken("it.checkout.consume@techstore.test", "CLIENTE");
        long product = product("IT-CHECKOUT-CONSUME", "Cable", "1000.00", 6);
        add(token, product, 2);
        long orderId = orderId(confirm(token, BODY).andExpect(status().isCreated())
                .andReturn());
        inventory.consumeReservations(orderId);
        inventory.consumeReservations(orderId);
        inventory.releaseReservations(orderId);
        assertEquals(4, stock(product));
        assertEquals("CONSUMED", jdbc.queryForObject(
                "SELECT status FROM stock_reservations WHERE order_id=?",
                String.class, orderId));
    }

    private long orderId(MvcResult r) throws Exception {
        return JSON.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions confirm(
            String token, String body) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String userAndToken(String email, String role) throws Exception {
        jdbc.update("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES(?,?,?,?)
                """, "Cliente", email, passwords.hash(PASSWORD).value(), role);
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(r.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long product(String sku, String name, String price, int quantity) {
        return jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?,?,?,?,TRUE) RETURNING id
                """, Long.class, sku, name, new BigDecimal(price), quantity);
    }

    private int stock(long productId) {
        return jdbc.queryForObject(
                "SELECT stock FROM products WHERE id=?",
                Integer.class, productId);
    }

    private void add(String token, long productId, int quantity)
            throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId
                        + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
