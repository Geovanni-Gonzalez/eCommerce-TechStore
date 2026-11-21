package com.techstore.orders.payments;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.orders.service.OrderReservationService;
import com.techstore.payments.PaymentAttemptRepository;
import com.techstore.payments.PaymentAttemptService;
import com.techstore.payments.PaymentAuthorization;
import com.techstore.payments.PaymentOutcome;
import com.techstore.payments.SandboxPaymentGateway;
import com.techstore.payments.webhook.VerifiedSandboxWebhookEvent;
import com.techstore.payments.webhook.VerifiedWebhookEventRepository;
import com.techstore.users.security.PasswordHasher;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * SOLO sandbox aislado: firma real HMAC, transacciones PostgreSQL y cero cobros.
 * No ejecutar contra una BD distinta a techstore_test.
 */
@SpringBootTest(properties = {
    "techstore.payment.provider=SANDBOX",
    "techstore.payment.sandbox.enabled=true",
    "techstore.payment.sandbox.outcome=APPROVED",
    "techstore.payment.webhook.enabled=true",
    "techstore.payment.webhook.secret=test-only-webhook-hmac-key-0123456789abcdef",
    "techstore.payment.sandbox.auto-confirm-verified-events=true"
})
@ActiveProfiles("integration")
class SandboxPaymentConfirmationIT {
    private static final String SECRET =
            "test-only-webhook-hmac-key-0123456789abcdef";
    private static final String WEBHOOK = "/api/payments/webhooks/sandbox";
    private static final String PASSWORD = "ClaveDePrueba2026";

    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired VerifiedWebhookEventRepository events;
    @Autowired OrderReservationService lifecycle;
    @Autowired SandboxPaymentConfirmationService confirmation;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PasswordHasher passwords;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private long userId, productId, orderId;
    private final List<Long> otherUserIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        assertEquals("techstore_test", jdbc.queryForObject(
                "SELECT current_database()", String.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        userId = jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Pago aprobado', ?, ?, 'CLIENTE') RETURNING id
                """, Long.class, "it.confirm." + suffix + "@techstore.test",
                "$2a$12$" + "A".repeat(53));
        productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Confirmación sandbox', 1500.00, 5, TRUE) RETURNING id
                """, Long.class, "CONF-" + suffix);
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 2));
        Order order = checkout.confirm(userId, new ShippingAddress(
                "San José", "Central", "Carmen",
                "Casa frente al parque central", "Ana Pérez", "8888-1111"));
        orderId = order.id();
    }

    @Test
    void signedApprovalConfirmsAndConsumesExactlyOnce() throws Exception {
        var attempt = authorize(PaymentOutcome.APPROVED);
        String eventId = uniqueEvent();
        String raw = payload(eventId, attempt.providerReference(),
                attempt.totalCrc().toPlainString(), "APPROVED");
        long now = Instant.now().getEpochSecond();
        mvc.perform(signedPost(raw, now))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.duplicate").value(false));
        assertEquals("CONFIRMED", orderStatus());
        assertEquals("CONSUMED", reservationStatus());
        assertEquals(3, stock());
        assertEquals("CONFIRMED_SIMULATED", reconciliationStatus());
        assertEquals(2, historyCount());
        assertEquals("SANDBOX_VERIFIED", lastHistoryReason());
        assertEquals("SYSTEM", lastActor());
        mvc.perform(signedPost(raw, now))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
        assertEquals("CONFIRMED", orderStatus());
        assertEquals(3, stock());
        assertEquals(2, historyCount());
        assertEquals(1, eventCount());
        assertFalse(lifecycle.expireOne(orderId));
    }

    @Test
    void noUnverifiedCallbackCanConfirmAnOrder() throws Exception {
        var attempt = authorize(PaymentOutcome.APPROVED);
        String raw = payload(uniqueEvent(), attempt.providerReference(),
                attempt.totalCrc().toPlainString(), "APPROVED");
        long now = Instant.now().getEpochSecond();
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                .content(raw)).andExpect(status().isUnauthorized());
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                .content(raw.replace("APPROVED", "DECLINED"))
                .header("X-TechStore-Webhook-Timestamp", Long.toString(now))
                .header("X-TechStore-Webhook-Signature", sign(now, raw)))
                .andExpect(status().isUnauthorized());
        assertEquals(0, eventCount());
        assertEquals("PENDING", orderStatus());
        assertEquals("RESERVED", reservationStatus());
        assertEquals(3, stock());
        assertEquals(1, historyCount());
    }

    @Test
    void declinedSignedEventNeverConfirmsOrConsumes() throws Exception {
        var attempt = authorize(PaymentOutcome.DECLINED);
        String raw = payload(uniqueEvent(), attempt.providerReference(),
                attempt.totalCrc().toPlainString(), "DECLINED");
        mvc.perform(signedPost(raw, Instant.now().getEpochSecond()))
                .andExpect(status().isAccepted());
        assertEquals("DECLINED", reconciliationStatus());
        assertEquals("PENDING", orderStatus());
        assertEquals("RESERVED", reservationStatus());
        assertEquals(3, stock());
        assertEquals(1, historyCount());
    }

    @Test
    void cancelledOrderKeepsLateApprovalForManualReconciliation() throws Exception {
        var attempt = authorize(PaymentOutcome.APPROVED);
        lifecycle.cancelOwned(userId, orderId);
        String raw = payload(uniqueEvent(), attempt.providerReference(),
                attempt.totalCrc().toPlainString(), "APPROVED");
        mvc.perform(signedPost(raw, Instant.now().getEpochSecond()))
                .andExpect(status().isAccepted());
        assertEquals("NEEDS_REVIEW", reconciliationStatus());
        assertEquals("CANCELLED", orderStatus());
        assertEquals("RELEASED", reservationStatus());
        assertEquals(5, stock());
        assertEquals(2, historyCount());
        assertEquals("CLIENT_CANCEL", lastHistoryReason());
    }

    @Test
    void expiredReservationKeepsEventAndBlocksConfirmation() throws Exception {
        var attempt = authorize(PaymentOutcome.APPROVED);
        jdbc.update("""
                UPDATE stock_reservations
                SET expires_at=CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE order_id=?
                """, orderId);
        mvc.perform(signedPost(payload(uniqueEvent(), attempt.providerReference(),
                attempt.totalCrc().toPlainString(), "APPROVED"),
                Instant.now().getEpochSecond())).andExpect(status().isAccepted());
        assertEquals("NEEDS_REVIEW", reconciliationStatus());
        assertEquals("PENDING", orderStatus());
        assertEquals("RESERVED", reservationStatus());
        assertEquals(3, stock());
        assertEquals(1, historyCount());
        assertTrue(lifecycle.expireOne(orderId));
        assertEquals(5, stock());
        assertEquals("CANCELLED", orderStatus());
    }

    @Test
    void mismatchedReservationQuantityIsNotSilentlyConsumed() throws Exception {
        var attempt = authorize(PaymentOutcome.APPROVED);
        jdbc.update("""
                UPDATE stock_reservations SET quantity=quantity-1
                WHERE order_id=? AND quantity > 1
                """, orderId);
        mvc.perform(signedPost(payload(uniqueEvent(), attempt.providerReference(),
                attempt.totalCrc().toPlainString(), "APPROVED"),
                Instant.now().getEpochSecond())).andExpect(status().isAccepted());
        assertEquals("NEEDS_REVIEW", reconciliationStatus());
        assertEquals("PENDING", orderStatus());
        assertEquals("RESERVED", reservationStatus());
        assertEquals(3, stock());
        assertEquals(1, historyCount());
    }

    @Test
    void rollbackRevertsOrderStockReservationHistoryAndEventUpdate() {
        var attempt = authorize(PaymentOutcome.APPROVED);
        var e = new VerifiedSandboxWebhookEvent(uniqueEvent(),
                attempt.providerReference(), orderId, attempt.totalCrc(),
                PaymentOutcome.APPROVED);
        long eventId = events.record(e, "A".repeat(64)).id();
        assertEquals("RECEIVED", reconciliationStatus());

        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            assertEquals(PaymentReconciliationStatus.CONFIRMED_SIMULATED,
                    confirmation.reconcile(eventId).status());
            assertEquals("CONFIRMED", orderStatus());
            assertEquals("CONSUMED", reservationStatus());
            assertEquals("CONFIRMED_SIMULATED", reconciliationStatus());
            tx.setRollbackOnly();
        });

        assertEquals("RECEIVED", reconciliationStatus());
        assertEquals("PENDING", orderStatus());
        assertEquals("RESERVED", reservationStatus());
        assertEquals(3, stock());
        assertEquals(1, historyCount());
        assertEquals(PaymentReconciliationStatus.CONFIRMED_SIMULATED,
                confirmation.reconcile(eventId).status());
        assertEquals(2, historyCount());
    }

    @Test
    void concurrentReconciliationCannotConsumeOrWriteHistoryTwice()
            throws Exception {
        var attempt = authorize(PaymentOutcome.APPROVED);
        long eventId = events.record(new VerifiedSandboxWebhookEvent(
                uniqueEvent(), attempt.providerReference(), orderId,
                attempt.totalCrc(), PaymentOutcome.APPROVED),
                "B".repeat(64)).id();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<PaymentReconciliationResult> task = () -> {
                ready.countDown();
                assertTrue(go.await(10, TimeUnit.SECONDS));
                return confirmation.reconcile(eventId);
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(PaymentReconciliationStatus.CONFIRMED_SIMULATED,
                    first.get(25, TimeUnit.SECONDS).status());
            assertEquals(PaymentReconciliationStatus.CONFIRMED_SIMULATED,
                    second.get(25, TimeUnit.SECONDS).status());
        }
        assertEquals("CONFIRMED", orderStatus());
        assertEquals("CONSUMED", reservationStatus());
        assertEquals(3, stock());
        assertEquals(2, historyCount());
        assertEquals(1, eventCount());
    }

    @Test
    void adminCanRetryPreviouslyVerifiedEventWithoutFabricatingAmount()
            throws Exception {
        var attempt = authorize(PaymentOutcome.APPROVED);
        long eventId = events.record(new VerifiedSandboxWebhookEvent(
                uniqueEvent(), attempt.providerReference(), orderId,
                attempt.totalCrc(), PaymentOutcome.APPROVED),
                "C".repeat(64)).id();
        String endpoint = "/api/admin/payment-events/" + eventId + "/reconcile";
        mvc.perform(post(endpoint)).andExpect(status().isUnauthorized());
        String client = userAndToken("CLIENTE");
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        String admin = userAndToken("ADMIN");
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/payment-events/999999999/reconcile")
                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_SIMULATED"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_SIMULATED"));
        assertEquals(2, historyCount());
        assertEquals(3, stock());
    }

    private com.techstore.payments.PaymentAttemptSnapshot authorize(
            PaymentOutcome outcome) {
        var service = new PaymentAttemptService(attempts,
                new SandboxPaymentGateway(outcome));
        return service.submit(userId, orderId,
                "key-confirm-" + UUID.randomUUID().toString().substring(0, 12))
                .attempt();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            signedPost(String raw, long epoch) throws Exception {
        return post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                .content(raw)
                .header("X-TechStore-Webhook-Timestamp", Long.toString(epoch))
                .header("X-TechStore-Webhook-Signature", sign(epoch, raw));
    }

    private String payload(String eventId, String ref, String crc, String outcome) {
        return """
                {"eventId":"%s","providerReference":"%s",
                 "orderId":%d,"totalCrc":%s,"outcome":"%s"}
                """.formatted(eventId, ref, orderId, crc, outcome);
    }

    private static String uniqueEvent() {
        return "evt-confirm-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private static String sign(long epoch, String raw) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(Long.toString(epoch).getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) '.');
        return "v1=" + HexFormat.of().formatHex(
                mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private String userAndToken(String role) throws Exception {
        String email = "it.confirm.auth." + UUID.randomUUID() + "@techstore.test";
        Long id = jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES(?,?,?,?) RETURNING id
                """, Long.class, "Usuario CI",
                email, passwords.hash(PASSWORD).value(), role);
        otherUserIds.add(id);
        var result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\""
                    + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
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
    private String reconciliationStatus() {
        return jdbc.queryForObject("""
                SELECT reconciliation_status FROM payment_webhook_events e
                JOIN payment_attempts a ON a.id=e.payment_attempt_id
                WHERE a.order_id=? LIMIT 1
                """, String.class, orderId);
    }
    private int eventCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_webhook_events e
                JOIN payment_attempts a ON a.id=e.payment_attempt_id
                WHERE a.order_id=?
                """, Integer.class, orderId);
    }
    private int historyCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_status_history WHERE order_id=?
                """, Integer.class, orderId);
    }
    private String lastHistoryReason() {
        return jdbc.queryForObject("""
                SELECT reason FROM order_status_history
                WHERE order_id=? ORDER BY id DESC LIMIT 1
                """, String.class, orderId);
    }
    private String lastActor() {
        return jdbc.queryForObject("""
                SELECT actor_type FROM order_status_history
                WHERE order_id=? ORDER BY id DESC LIMIT 1
                """, String.class, orderId);
    }
    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id=?",
                Integer.class, productId);
    }

    @AfterEach
    void cleanup() {
        if (userId < 1) return;
        jdbc.update("""
                DELETE FROM payment_webhook_events
                WHERE payment_attempt_id IN (SELECT id FROM payment_attempts
                    WHERE order_id IN (SELECT id FROM orders WHERE user_id=?))
                """, userId);
        jdbc.update("""
                DELETE FROM sandbox_checkout_sessions
                WHERE payment_attempt_id IN (SELECT id FROM payment_attempts
                    WHERE order_id IN (SELECT id FROM orders WHERE user_id=?))
                """, userId);
        jdbc.update("""
                DELETE FROM payment_attempts WHERE order_id IN
                    (SELECT id FROM orders WHERE user_id=?)
                """, userId);
        jdbc.update("""
                DELETE FROM order_status_history WHERE order_id IN
                    (SELECT id FROM orders WHERE user_id=?)
                """, userId);
        jdbc.update("""
                DELETE FROM order_event_audit WHERE order_id IN
                    (SELECT id FROM orders WHERE user_id=?)
                """, userId);
        jdbc.update("""
                DELETE FROM stock_reservations WHERE order_id IN
                    (SELECT id FROM orders WHERE user_id=?)
                """, userId);
        jdbc.update("""
                DELETE FROM order_items WHERE order_id IN
                    (SELECT id FROM orders WHERE user_id=?)
                """, userId);
        jdbc.update("DELETE FROM orders WHERE user_id=?", userId);
        jdbc.update("""
                DELETE FROM cart_items WHERE cart_id IN
                    (SELECT id FROM carts WHERE user_id=?)
                """, userId);
        jdbc.update("DELETE FROM carts WHERE user_id=?", userId);
        jdbc.update("DELETE FROM products WHERE id=?", productId);
        jdbc.update("DELETE FROM users WHERE id=?", userId);
        for (Long otherId : otherUserIds) {
            jdbc.update("DELETE FROM auth_sessions WHERE user_id=?", otherId);
            jdbc.update("DELETE FROM users WHERE id=?", otherId);
        }
        otherUserIds.clear();
    }
}
