package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.orders.service.OrderReservationService;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * BKL-042: pruebas HTTP con Bearer/roles y PostgreSQL, aisladas por rollback.
 * Se comprueba IDOR, no exposición de PII en listados, paginación y auditoría.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class OrderQueryHttpIT {
    private static final String PASSWORD = "ClaveDePrueba2026";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHasher passwords;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;
    @Autowired OrderReservationService lifecycle;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private String alice, bob, admin;
    private long aliceId, bobId, productId;

    @BeforeEach
    void setup() throws Exception {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        alice = createUser("CLIENTE");
        aliceId = lastUserId();
        bob = createUser("CLIENTE");
        bobId = lastUserId();
        admin = createUser("ADMIN");
        productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Teclado original',1250.25,100,TRUE) RETURNING id
                """, Long.class, "QUERY-" + UUID.randomUUID());
    }

    @Test
    void onlyBearerClientCanReadTheirRoutesAndOnlyAdminCanReadAdminRoutes()
            throws Exception {
        long order = createOrder(aliceId).id();

        for (String url : new String[]{
                "/api/orders",
                "/api/orders/" + order,
                "/api/orders/" + order + "/history"
        }) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code")
                            .value("AUTHENTICATION_REQUIRED"));
            mvc.perform(get(url).header("Authorization", bearer(admin)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        for (String url : new String[]{
                "/api/admin/orders",
                "/api/admin/orders/" + order,
                "/api/admin/orders/" + order + "/history"
        }) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header("Authorization", bearer(alice)))
                    .andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization", bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
    }

    @Test
    void listingUsesSqlOwnershipAndStableCursorWithoutShippingPii()
            throws Exception {
        long a1 = createOrder(aliceId).id();
        long b1 = createOrder(bobId).id();
        long a2 = createOrder(aliceId).id();
        long b2 = createOrder(bobId).id();
        long a3 = createOrder(aliceId).id();
        String url = "/api/orders?limit=2&userId=" + bobId;
        MvcResult first = mvc.perform(get(url).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(a3))
                .andExpect(jsonPath("$.items[1].id").value(a2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextBeforeId").value(a2))
                .andExpect(jsonPath("$.items[0].totals.totalCrc").isNumber())
                .andExpect(jsonPath("$.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.items[0].ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.items[0].shippingAddress").doesNotExist())
                .andReturn();
        assertFalse(first.getResponse().getContentAsString()
                .contains("Casa de entrega"));

        mvc.perform(get("/api/orders?limit=2&beforeId=" + a2)
                    .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(a1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId").isEmpty());
        mvc.perform(get("/api/orders").header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(b2))
                .andExpect(jsonPath("$.items[1].id").value(b1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void crossUserIdorIsIndistinguishableFromMissingOrder()
            throws Exception {
        long aliceOrder = createOrder(aliceId).id();
        long bobOrder = createOrder(bobId).id();
        for (String endpoint : new String[]{
                "/api/orders/" + bobOrder,
                "/api/orders/" + bobOrder + "/history",
                "/api/orders/999999999",
                "/api/orders/999999999/history"
        }) {
            mvc.perform(get(endpoint).header("Authorization", bearer(alice)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(get("/api/orders/" + aliceOrder)
                .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceOrder))
                .andExpect(jsonPath("$.userId").doesNotExist());
        mvc.perform(get("/api/orders/" + bobOrder)
                .header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bobOrder));
    }

    @Test
    void detailKeepsCommercialSnapshotsWhenProductCatalogChanges()
            throws Exception {
        long id = createOrder(aliceId).id();
        jdbc.update("""
                UPDATE products SET name='Teclado posterior',
                    price_crc=9999.00,active=FALSE WHERE id=?
                """, productId);
        mvc.perform(get("/api/orders/" + id)
                .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentMode").value("SIMULATED"))
                .andExpect(jsonPath("$.items[0].name").value("Teclado original"))
                .andExpect(jsonPath("$.items[0].unitPriceCrc").value(1250.25))
                .andExpect(jsonPath("$.shippingAddress.details")
                        .value("Casa de entrega junto a la iglesia"))
                .andExpect(jsonPath("$.totals.totalCrc").isNumber());
    }

    @Test
    void adminCanInspectAllOrdersWhileClientNeverSeesActorIds()
            throws Exception {
        long aliceOrder = createOrder(aliceId).id();
        long bobOrder = createOrder(bobId).id();
        lifecycle.cancelOwned(aliceId, aliceOrder);

        mvc.perform(get("/api/orders/" + aliceOrder + "/history")
                .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].toStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].reason").value("CREATED"))
                .andExpect(jsonPath("$[1].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$[1].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$[1].reason").value("CLIENT_CANCEL"))
                .andExpect(jsonPath("$[1].actorType").value("CLIENT"))
                .andExpect(jsonPath("$[1].actorId").doesNotExist())
                .andExpect(jsonPath("$[1].id").doesNotExist())
                .andExpect(jsonPath("$[1].orderId").doesNotExist());
        mvc.perform(get("/api/admin/orders/" + aliceOrder + "/history")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].actorId").value(aliceId));

        mvc.perform(get("/api/admin/orders?limit=1")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].ownerUserId").value(bobId))
                .andExpect(jsonPath("$.items[0].order.id").value(bobOrder))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextBeforeId").value(bobOrder));

        mvc.perform(get("/api/admin/orders?limit=1&beforeId=" + bobOrder)
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].order.id").value(aliceOrder))
                .andExpect(jsonPath("$.items[0].ownerUserId").value(aliceId))
                .andExpect(jsonPath("$.hasMore").value(false));

        mvc.perform(get("/api/admin/orders/" + aliceOrder)
                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").value(aliceId))
                .andExpect(jsonPath("$.order.id").value(aliceOrder))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
        mvc.perform(get("/api/admin/orders/999999999")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void emptyClientListingAndBoundedParametersFailClosed() throws Exception {
        mvc.perform(get("/api/orders").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextBeforeId").isEmpty());
        for (String query : new String[]{
                "?limit=0", "?limit=-1", "?limit=101", "?beforeId=0",
                "?beforeId=-50", "?limit=abc", "?beforeId=invalid",
                "?beforeId=999999999999999999999999999999"
        }) {
            mvc.perform(get("/api/orders" + query)
                    .header("Authorization", bearer(alice)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ORDER_QUERY"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(get("/api/admin/orders?limit=101")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_QUERY"));
        mvc.perform(get("/api/orders/-2")
                .header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound());
    }


    @Test
    void bkl049VerticalAndHorizontalAccessToShippingAndReviewsFailsClosed()
            throws Exception {
        long aliceOrder = createOrder(aliceId).id();
        long bobOrder = createOrder(bobId).id();

        // El seguimiento del pedido ajeno es indistinguible del inexistente.
        for (String target : new String[]{
                "/api/orders/" + bobOrder + "/shipping",
                "/api/orders/999999999/shipping"
        }) {
            mvc.perform(get(target).header("Authorization", bearer(alice)))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(get("/api/orders/" + aliceOrder + "/shipping")
                .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/orders/" + aliceOrder + "/shipping"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/" + aliceOrder + "/shipping")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());

        // Un administrador no puede aprovechar el endpoint CLIENT de reseñas.
        String eligibility = "/api/reviews/products/" + productId + "/eligibility";
        String review = "/api/reviews/products/" + productId;
        mvc.perform(get(eligibility)).andExpect(status().isUnauthorized());
        mvc.perform(get(eligibility).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(post(review).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"comment\":\"Excelente producto\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(review).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearer(admin))
                .content("{\"rating\":5,\"comment\":\"Excelente producto\"}"))
                .andExpect(status().isForbidden());

        // El listado público no incorpora el acceso privado del cliente.
        mvc.perform(get(review))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Order createOrder(long userId) {
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 1));
        return checkout.confirm(userId, new ShippingAddress(
                "San José", "Central", "Carmen",
                "Casa de entrega junto a la iglesia", "Ana Pérez", "8888-1111"));
    }

    private String createUser(String role) throws Exception {
        String email = "it.orderquery." + UUID.randomUUID() + "@techstore.test";
        jdbc.update("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES(?,?,?,?)
                """, "Cliente de prueba", email, passwords.hash(PASSWORD).value(), role);
        MvcResult response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\""
                        + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(response.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long lastUserId() {
        return jdbc.queryForObject("SELECT MAX(id) FROM users", Long.class);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
