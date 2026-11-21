package com.techstore.payments.webhook;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.payments.PaymentAttemptRepository;
import com.techstore.payments.PaymentAttemptService;
import com.techstore.payments.PaymentAttemptStatus;
import com.techstore.payments.PaymentOutcome;
import com.techstore.payments.SandboxPaymentGateway;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

/** HTTP HMAC real: hechos simulados firmados, nunca confirmación de pedido. */
@SpringBootTest(properties = {
    "techstore.payment.webhook.enabled=true",
    "techstore.payment.webhook.secret=test-only-webhook-hmac-key-0123456789abcdef"
})
@ActiveProfiles("integration")
@Transactional
class SandboxWebhookHttpIT {
    private static final String SECRET = "test-only-webhook-hmac-key-0123456789abcdef";
    private static final String URL = "/api/payments/webhooks/sandbox";

    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private long orderId;
    private long productId;
    private String providerReference;
    private java.math.BigDecimal total;

    @BeforeEach
    void setup() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        long userId = jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Webhook prueba', ?, ?, 'CLIENTE') RETURNING id
                """, Long.class, "it.webhook." + suffix + "@techstore.test",
                "$2a$12$" + "A".repeat(53));
        productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Webhook producto', 1500.00, 5, TRUE) RETURNING id
                """, Long.class, "WB-" + suffix);
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 1));
        Order order = checkout.confirm(userId, new ShippingAddress(
                "San José", "Central", "Carmen",
                "Casa frente al parque central", "Ana Pérez", "8888-1111"));
        orderId = order.id();
        var sandbox = new PaymentAttemptService(attempts,
                new SandboxPaymentGateway(PaymentOutcome.APPROVED));
        var attempt = sandbox.submit(userId, orderId, "it-webhook-key-" + suffix).attempt();
        assertEquals(PaymentAttemptStatus.SIMULATED_APPROVED, attempt.status());
        total = attempt.totalCrc();
        providerReference = attempt.providerReference();
    }

    @Test
    void signedEventAcceptedOnceAndDoesNotMutateOrdersOrStock() throws Exception {
        String body = payload("event-accepted-0001", providerReference, "APPROVED");
        long epoch = Instant.now().getEpochSecond();
        mvc.perform(signedPost(body, epoch))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.eventId").value("event-accepted-0001"));
        mvc.perform(signedPost(body, epoch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
        assertEquals(1, countEvents());
        assertEquals("PENDING", orderStatus());
        assertEquals("RESERVED", reservationStatus());
        assertEquals(4, currentStock());
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_status_history WHERE order_id=?
                """, Integer.class, orderId));
    }

    @Test
    void rejectsUnsignedTamperedStaleFutureAndOversizedBodies() throws Exception {
        String valid = payload("event-auth-0001", providerReference, "APPROVED");
        long now = Instant.now().getEpochSecond();
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_AUTHENTICATION_FAILED"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace("APPROVED", "DECLINED"))
                        .header("X-TechStore-Webhook-Timestamp", Long.toString(now))
                        .header("X-TechStore-Webhook-Signature", sign(now, valid)))
                .andExpect(status().isUnauthorized());
        mvc.perform(signedPost(valid, now - 301))
                .andExpect(status().isUnauthorized());
        mvc.perform(signedPost(valid, now + 301))
                .andExpect(status().isUnauthorized());
        mvc.perform(signedPost("x".repeat(16 * 1024 + 1), now))
                .andExpect(status().isBadRequest());
        assertEquals(0, countEvents());
        assertEquals("PENDING", orderStatus());
    }

    @Test
    void rejectsSignedUnknownReferenceWrongAmountOutcomeAndExtraProperties()
            throws Exception {
        String valid = payload("event-invalid-0001", providerReference, "APPROVED");
        long epoch = Instant.now().getEpochSecond();
        mvc.perform(signedPost(valid.replace(providerReference,
                    "SBX-FFFFFFFFFFFFFFFFFFFFFFFF"), epoch))
                .andExpect(status().isConflict());
        mvc.perform(signedPost(valid.replace(total.toPlainString(), "1.00"), epoch))
                .andExpect(status().isConflict());
        mvc.perform(signedPost(valid.replace("APPROVED", "DECLINED"), epoch))
                .andExpect(status().isConflict());
        mvc.perform(signedPost(valid.replace("}", ",\"card\":\"fake\"}"), epoch))
                .andExpect(status().isBadRequest());
        assertEquals(0, countEvents());
    }

    @Test
    void signedConflictingEventIdAndSecondEventForAttemptReturn409()
            throws Exception {
        String first = payload("event-reuse-0001", providerReference, "APPROVED");
        long epoch = Instant.now().getEpochSecond();
        mvc.perform(signedPost(first, epoch)).andExpect(status().isAccepted());
        mvc.perform(signedPost(
                payload("event-reuse-0002", providerReference, "APPROVED"), epoch))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WEBHOOK_EVENT_CONFLICT"));
        // Mismo eventId y otro cuerpo válido firmado: no se sobrescribe.
        mvc.perform(signedPost(" " + first, epoch))
                .andExpect(status().isConflict());
        assertEquals(1, countEvents());
        assertEquals("PENDING", orderStatus());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            signedPost(String body, long epoch) throws Exception {
        return post(URL).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-TechStore-Webhook-Timestamp", Long.toString(epoch))
                .header("X-TechStore-Webhook-Signature", sign(epoch, body));
    }

    private String payload(String eventId, String reference, String outcome) {
        return """
                {"eventId":"%s","providerReference":"%s",
                 "orderId":%d,"totalCrc":%s,"outcome":"%s"}
                """.formatted(eventId, reference, orderId,
                        total.toPlainString(), outcome);
    }

    private static String sign(long epoch, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(Long.toString(epoch).getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) '.');
        return "v1=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private int countEvents() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_webhook_events e
                JOIN payment_attempts a ON a.id=e.payment_attempt_id
                WHERE a.order_id=?
                """, Integer.class, orderId);
    }

    private String orderStatus() {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?",
                String.class, orderId);
    }

    private String reservationStatus() {
        return jdbc.queryForObject("""
                SELECT status FROM stock_reservations WHERE order_id=? LIMIT 1
                """, String.class, orderId);
    }

    private int currentStock() {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id=?",
                Integer.class, productId);
    }
}
