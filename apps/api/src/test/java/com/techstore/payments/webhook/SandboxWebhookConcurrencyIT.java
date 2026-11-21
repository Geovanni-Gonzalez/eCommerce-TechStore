package com.techstore.payments.webhook;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.payments.PaymentAttemptRepository;
import com.techstore.payments.PaymentAttemptService;
import com.techstore.payments.PaymentOutcome;
import com.techstore.payments.SandboxPaymentGateway;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Dos transacciones reales en PostgreSQL: jamás se insertan dos eventos. */
@SpringBootTest
@ActiveProfiles("integration")
class SandboxWebhookConcurrencyIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired VerifiedWebhookEventRepository events;
    private long userId;
    private long productId;
    private long orderId;
    private String reference;
    private java.math.BigDecimal total;

    @BeforeEach
    void setup() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        userId = jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Webhook concorrente', ?, ?, 'CLIENTE') RETURNING id
                """, Long.class, "it.webhook.concurrent." + suffix + "@techstore.test",
                "$2a$12$" + "A".repeat(53));
        productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Webhook concurrency', 1500.00, 5, TRUE) RETURNING id
                """, Long.class, "WBC-" + suffix);
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 1));
        Order order = checkout.confirm(userId, new ShippingAddress(
                "San José", "Central", "Carmen",
                "Casa frente al parque central", "Ana Pérez", "8888-1111"));
        orderId = order.id();
        var response = new PaymentAttemptService(attempts,
                new SandboxPaymentGateway(PaymentOutcome.APPROVED))
                .submit(userId, orderId, "key-webhook-" + suffix).attempt();
        reference = response.providerReference();
        total = response.totalCrc();
    }

    @Test
    void concurrentSameEventReturnsOneCreationAndOneReplay() throws Exception {
        var event = new VerifiedSandboxWebhookEvent(
                "evt-concurrent-" + UUID.randomUUID().toString().substring(0, 12),
                reference, orderId, total, PaymentOutcome.APPROVED);
        String payloadDigest = "B".repeat(64);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<WebhookEventReceipt> operation = () -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return events.record(event, payloadDigest);
            };
            var a = pool.submit(operation);
            var b = pool.submit(operation);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            WebhookEventReceipt one = a.get(25, TimeUnit.SECONDS);
            WebhookEventReceipt two = b.get(25, TimeUnit.SECONDS);
            assertEquals(one.id(), two.id());
            assertEquals(1, (one.duplicate() ? 0 : 1) + (two.duplicate() ? 0 : 1));
        }
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_webhook_events e
                JOIN payment_attempts a ON a.id=e.payment_attempt_id
                WHERE a.order_id=?
                """, Integer.class, orderId));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM orders WHERE id=?", String.class, orderId));
        assertEquals(4, jdbc.queryForObject(
                "SELECT stock FROM products WHERE id=?", Integer.class, productId));
    }

    @Test
    void differentEventIdForSameAttemptIsRejectedWithoutStateMutation() {
        var first = new VerifiedSandboxWebhookEvent(
                "evt-first-001", reference, orderId, total, PaymentOutcome.APPROVED);
        var second = new VerifiedSandboxWebhookEvent(
                "evt-second-002", reference, orderId, total, PaymentOutcome.APPROVED);
        assertFalse(events.record(first, "C".repeat(64)).duplicate());
        assertThrows(WebhookConflictException.class,
                () -> events.record(second, "D".repeat(64)));
        assertThrows(WebhookConflictException.class,
                () -> events.record(first, "E".repeat(64)));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_webhook_events WHERE provider_event_id=?
                """, Integer.class, first.eventId()));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM orders WHERE id=?", String.class, orderId));
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
    }
}
