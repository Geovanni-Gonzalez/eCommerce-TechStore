package com.techstore.payments.sessions;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.orders.service.OrderReservationService;
import com.techstore.payments.PaymentAttemptConflictException;
import com.techstore.payments.PaymentAttemptStatus;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL real, commmits separados: BKL-037 no crea cargos bancarios. */
@SpringBootTest(properties={
        "techstore.payment.provider=SANDBOX",
        "techstore.payment.sandbox.enabled=true",
        "techstore.payment.sandbox.outcome=APPROVED"
})
@ActiveProfiles("integration")
class SandboxCheckoutSessionIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;
    @Autowired SandboxCheckoutSessionService sessions;
    @Autowired OrderReservationService lifecycle;
    @Autowired PlatformTransactionManager transactions;

    private long userId;
    private long productId;
    private long orderId;

    @BeforeEach
    void setup() {
        assertEquals("techstore_test", jdbc.queryForObject(
                "SELECT current_database()", String.class));
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        userId = jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Sesión pago', ?, ?, 'CLIENTE') RETURNING id
                """, Long.class, "it.session." + suffix + "@techstore.test",
                "$2a$12$" + "A".repeat(53));
        productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Sesión sandbox', 1500.00, 5, TRUE) RETURNING id
                """, Long.class, "SES-" + suffix);
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 2));
        Order order = checkout.confirm(userId, new ShippingAddress(
                "San José", "Central", "Carmen",
                "Casa frente al parque central", "Ana Pérez", "8888-1111"));
        orderId = order.id();
    }

    @Test
    void checkoutStartsWithCrcFromDbAndCompletesWithoutConfirmingOrder() {
        var result = sessions.start(userId, orderId, "key-hosted-0001");
        assertTrue(result.attemptCreated());
        var response = result.response();
        assertEquals(orderId, response.orderId());
        assertEquals("CRC", response.currency());
        assertTrue(response.totalCrc().signum() > 0);
        assertTrue(response.redirectUrl().startsWith(
                "http://localhost:3000/pagos/sandbox#session="));
        String token = tokenOf(response);
        assertEquals(response.totalCrc(), sessions.preview(token).totalCrc());
        assertEquals(1, countAttempts());
        assertEquals(1, countSessions());
        assertEquals("STARTED", attemptStatus());
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM sandbox_checkout_sessions WHERE token_hash=?
                """, Integer.class, token));
        assertEquals(64, jdbc.queryForObject("""
                SELECT LENGTH(token_hash) FROM sandbox_checkout_sessions
                WHERE payment_attempt_id=?
                """, Integer.class, response.attemptId()));
        var completed = sessions.complete(token);
        assertEquals(PaymentAttemptStatus.SIMULATED_APPROVED, completed.status());
        assertTrue(completed.providerReference().startsWith("SBX-"));
        assertEquals(orderId, completed.orderId());
        assertThrows(SandboxSessionUnavailableException.class,
                () -> sessions.complete(token));
        assertThrows(SandboxSessionUnavailableException.class,
                () -> sessions.preview(token));
        assertThrows(SandboxSessionConflictException.class,
                () -> sessions.start(userId, orderId, "key-hosted-0001"));
        assertEquals(1, countAttempts());
        assertEquals("PENDING", orderStatus());
        assertEquals("RESERVED", reservationStatus());
        assertEquals(3, currentStock());
    }

    @Test
    void reissuedSameIdempotencyKeyRotatesTokenWithoutExtraAttempt() {
        var first = sessions.start(userId, orderId, "key-rotated-0001");
        var second = sessions.start(userId, orderId, "key-rotated-0001");
        assertTrue(first.attemptCreated());
        assertFalse(second.attemptCreated());
        assertEquals(first.response().attemptId(), second.response().attemptId());
        assertNotEquals(first.response().redirectUrl(), second.response().redirectUrl());
        assertThrows(SandboxSessionUnavailableException.class,
                () -> sessions.preview(tokenOf(first.response())));
        assertEquals(second.response().attemptId(),
                sessions.preview(tokenOf(second.response())).attemptId());
        assertEquals(1, countSessions());
        assertEquals(1, countAttempts());
    }

    @Test
    void ownershipAndDifferentKeyCannotOverrideActiveSession() {
        assertThrows(com.techstore.payments.PaymentAttemptNotFoundException.class,
                () -> sessions.start(userId + 50000, orderId, "key-foreign-001"));
        sessions.start(userId, orderId, "key-original-001");
        assertThrows(PaymentAttemptConflictException.class,
                () -> sessions.start(userId, orderId, "key-other-00001"));
        assertEquals(1, countAttempts());
    }

    @Test
    void staleOrCancelledOrdersCannotStartOrContinueCheckout() {
        jdbc.update("""
                UPDATE stock_reservations
                SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute'
                WHERE order_id=?
                """, orderId);
        assertThrows(PaymentAttemptConflictException.class,
                () -> sessions.start(userId, orderId, "key-expired-0001"));
        assertEquals(0, countAttempts());
    }

    @Test
    void existingSessionIsInvalidatedByCancelledOrder() {
        String token = tokenOf(sessions.start(userId, orderId,
                "key-cancelled-001").response());
        lifecycle.cancelOwned(userId, orderId);
        assertThrows(SandboxSessionUnavailableException.class,
                () -> sessions.preview(token));
        assertThrows(SandboxSessionUnavailableException.class,
                () -> sessions.complete(token));
        assertEquals("CANCELLED", orderStatus());
        assertEquals(5, currentStock());
    }

    @Test
    void expiredRedirectTokenIsInvalidEvenWhenReservationIsStillValid() {
        String token = tokenOf(sessions.start(userId, orderId,
                "key-timed-out-001").response());
        jdbc.update("""
                UPDATE sandbox_checkout_sessions
                SET created_at=CURRENT_TIMESTAMP-INTERVAL '20 minutes',
                    expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute'
                WHERE payment_attempt_id=(SELECT id FROM payment_attempts
                    WHERE order_id=?)
                """, orderId);
        assertThrows(SandboxSessionUnavailableException.class,
                () -> sessions.preview(token));
        assertThrows(SandboxSessionUnavailableException.class,
                () -> sessions.complete(token));
    }

    @Test
    void concurrentStartsUseOnlyOneAttemptAndOneRotatedSession() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<SandboxCheckoutSessionService.StartResult> task = () -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return sessions.start(userId, orderId, "key-concurrent-001");
            };
            var a = pool.submit(task);
            var b = pool.submit(task);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var one = a.get(25, TimeUnit.SECONDS);
            var two = b.get(25, TimeUnit.SECONDS);
            assertEquals(one.response().attemptId(), two.response().attemptId());
            assertEquals(1, (one.attemptCreated() ? 1 : 0)
                    + (two.attemptCreated() ? 1 : 0));
        }
        assertEquals(1, countAttempts());
        assertEquals(1, countSessions());
        assertEquals("STARTED", attemptStatus());
    }

    @Test
    void concurrentRedemptionCanAuthorizeOnlyOnce() throws Exception {
        String token = tokenOf(sessions.start(userId, orderId,
                "key-one-time-001").response());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Object> task = () -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    return sessions.complete(token);
                } catch (SandboxSessionUnavailableException expected) {
                    return expected;
                }
            };
            var a = pool.submit(task);
            var b = pool.submit(task);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            Object one = a.get(25, TimeUnit.SECONDS);
            Object two = b.get(25, TimeUnit.SECONDS);
            assertEquals(1, (one instanceof com.techstore.payments.PaymentAttemptResponse ? 1 : 0)
                    + (two instanceof com.techstore.payments.PaymentAttemptResponse ? 1 : 0));
        }
        assertEquals(PaymentAttemptStatus.SIMULATED_APPROVED.name(), attemptStatus());
        assertEquals(1, countAttempts());
        assertEquals("PENDING", orderStatus());
    }

    @Test
    void claimAndSessionTogetherRollBackIfOuterTransactionRollsBack() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            sessions.start(userId, orderId, "key-rollback-001");
            assertEquals(1, countAttempts());
            assertEquals(1, countSessions());
            tx.setRollbackOnly();
        });
        assertEquals(0, countAttempts());
        assertEquals(0, countSessions());
    }

    private static String tokenOf(SandboxSessionStartedResponse response) {
        return response.redirectUrl().split("#session=", 2)[1];
    }

    private int countAttempts() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_attempts WHERE order_id=?
                """, Integer.class, orderId);
    }

    private int countSessions() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM sandbox_checkout_sessions
                WHERE payment_attempt_id IN
                    (SELECT id FROM payment_attempts WHERE order_id=?)
                """, Integer.class, orderId);
    }

    private String attemptStatus() {
        return jdbc.queryForObject("""
                SELECT status FROM payment_attempts WHERE order_id=?
                """, String.class, orderId);
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

    @AfterEach
    void cleanup() {
        if (userId < 1) return;
        jdbc.update("""
                DELETE FROM sandbox_checkout_sessions
                WHERE payment_attempt_id IN (SELECT id FROM payment_attempts
                    WHERE order_id IN (SELECT id FROM orders WHERE user_id=?))
                """, userId);
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
        if (productId > 0) jdbc.update("DELETE FROM products WHERE id=?", productId);
        jdbc.update("DELETE FROM users WHERE id=?", userId);
    }
}
