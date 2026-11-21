package com.techstore.payments;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.orders.service.OrderReservationService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Commits reales PostgreSQL. Nunca se ejecuta en una base distinta de techstore_test. */
@SpringBootTest
@ActiveProfiles("integration")
class PaymentAttemptIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;
    @Autowired PaymentAttemptService defaultService;
    @Autowired PaymentAttemptRepository repository;
    @Autowired OrderReservationService orderLifecycle;
    @Autowired PlatformTransactionManager transactions;

    private long userId;
    private long productId;
    private long orderId;

    @BeforeEach
    void setup() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        userId = jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Pago prueba', ?, ?, 'CLIENTE') RETURNING id
                """, Long.class, "it.attempt." + suffix + "@techstore.test",
                "$2a$12$" + "A".repeat(53));
        productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Pago producto', 1500.00, 5, TRUE) RETURNING id
                """, Long.class, "PAY-" + suffix);
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 2));
        Order order = checkout.confirm(userId, new ShippingAddress(
                "San José", "Central", "Carmen",
                "Casa frente al parque central", "Ana Pérez", "8888-1111"));
        orderId = order.id();
    }

    @Test
    void disabledGatewayPersistsUnavailableAndReplaysWithoutDuplicate() {
        var first = defaultService.submit(userId, orderId, "attempt-replay-001");
        var repeated = defaultService.submit(userId, orderId, "attempt-replay-001");
        assertTrue(first.created());
        assertFalse(repeated.created());
        assertEquals(first.attempt().id(), repeated.attempt().id());
        assertEquals(PaymentAttemptStatus.UNAVAILABLE, first.attempt().status());
        assertNull(first.attempt().providerReference());
        assertEquals(1, attemptCount());
        assertEquals(64, jdbc.queryForObject("""
                SELECT LENGTH(idempotency_key_hash) FROM payment_attempts WHERE id=?
                """, Integer.class, first.attempt().id()));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_attempts
                WHERE idempotency_key_hash='attempt-replay-001'
                """, Integer.class));
        assertEquals(first.attempt(), defaultService.findForOwner(
                userId, orderId, first.attempt().id()));
        assertEquals("PENDING", orderStatus());
        assertEquals(3, stock());
    }

    @Test
    void approvedSandboxBlocksNewKeysAndNeverConfirmsOrder() {
        var sandbox = new PaymentAttemptService(repository,
                new SandboxPaymentGateway(PaymentOutcome.APPROVED));
        var first = sandbox.submit(userId, orderId, "attempt-approved-001");
        var replay = sandbox.submit(userId, orderId, "attempt-approved-001");
        assertTrue(first.created());
        assertFalse(replay.created());
        assertEquals(PaymentAttemptStatus.SIMULATED_APPROVED, first.attempt().status());
        assertEquals(first.attempt(), replay.attempt());
        assertTrue(first.attempt().providerReference().startsWith("SBX-"));
        assertThrows(PaymentAttemptConflictException.class,
                () -> sandbox.submit(userId, orderId, "attempt-approved-002"));
        assertEquals(1, attemptCount());
        assertEquals("PENDING", orderStatus()); // BKL-040 NO está implementado.
        assertEquals("RESERVED", reservationStatus());
        assertEquals(3, stock());
    }

    @Test
    void declinesCanRetryUsingNewKeyButNotDuplicate() {
        var sandbox = new PaymentAttemptService(repository,
                new SandboxPaymentGateway(PaymentOutcome.DECLINED));
        var one = sandbox.submit(userId, orderId, "attempt-decline-001");
        var two = sandbox.submit(userId, orderId, "attempt-decline-002");
        assertEquals(PaymentAttemptStatus.SIMULATED_DECLINED, one.attempt().status());
        assertEquals(PaymentAttemptStatus.SIMULATED_DECLINED, two.attempt().status());
        assertNotEquals(one.attempt().providerReference(), two.attempt().providerReference());
        assertEquals(2, attemptCount());
        assertFalse(sandbox.submit(userId, orderId, "attempt-decline-001").created());
    }

    @Test
    void unknownResultCannotBeResentOrBypassedWithNewKey() {
        AtomicInteger calls = new AtomicInteger();
        PaymentAttemptService broken = new PaymentAttemptService(repository, req -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Falló después de enviar la autorización");
        });
        var first = broken.submit(userId, orderId, "attempt-uncertain-001");
        assertEquals(PaymentAttemptStatus.UNKNOWN, first.attempt().status());
        assertFalse(broken.submit(userId, orderId, "attempt-uncertain-001").created());
        assertEquals(1, calls.get());
        assertThrows(PaymentAttemptConflictException.class,
                () -> broken.submit(userId, orderId, "attempt-uncertain-002"));
        assertEquals(1, attemptCount());
    }

    @Test
    void startedClaimNeverCallsGatewayOnReplay() {
        String key = "attempt-started-001";
        String digest = java.util.HexFormat.of().withUpperCase().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var claim = repository.claim(userId, orderId, digest);
        assertTrue(claim.created());
        AtomicInteger calls = new AtomicInteger();
        var service = new PaymentAttemptService(repository, req -> {
            calls.incrementAndGet();
            return new PaymentAuthorization(PaymentOutcome.APPROVED, "SBX-123456789012345678901234");
        });
        assertEquals(PaymentAttemptStatus.STARTED,
                service.submit(userId, orderId, key).attempt().status());
        assertEquals(0, calls.get());
        assertThrows(PaymentAttemptConflictException.class,
                () -> service.submit(userId, orderId, "attempt-started-002"));
    }

    @Test
    void concurrencySameKeyCreatesOneRecordAndInvokesGatewayOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var service = new PaymentAttemptService(repository, request -> {
            calls.incrementAndGet();
            return new PaymentAuthorization(
                    PaymentOutcome.DECLINED, "SBX-ABCDEABCDEABCDEABCDEABCD");
        });
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<PaymentAttemptService.Submission> operation = () -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return service.submit(userId, orderId, "attempt-concurrent-001");
            };
            var first = pool.submit(operation);
            var second = pool.submit(operation);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var one = first.get(25, TimeUnit.SECONDS);
            var two = second.get(25, TimeUnit.SECONDS);
            assertEquals(one.attempt().id(), two.attempt().id());
            assertEquals(1, (one.created() ? 1 : 0) + (two.created() ? 1 : 0));
        }
        assertEquals(1, calls.get());
        assertEquals(1, attemptCount());
        assertEquals(PaymentAttemptStatus.SIMULATED_DECLINED,
                repository.findForOwner(userId, orderId,
                    jdbc.queryForObject("SELECT id FROM payment_attempts WHERE order_id=?",
                        Long.class, orderId)).status());
    }

    @Test
    void cannotReuseSameKeyForOtherOrderOrOtherOwner() {
        defaultService.submit(userId, orderId, "attempt-same-user");
        assertThrows(PaymentAttemptNotFoundException.class,
                () -> defaultService.submit(userId + 9000, orderId, "attempt-same-user"));
        assertThrows(PaymentAttemptNotFoundException.class,
                () -> defaultService.findForOwner(userId + 9000, orderId, 1));
        assertThrows(PaymentAttemptConflictException.class,
                () -> defaultService.submit(userId, orderId + 9000, "attempt-same-user"));
    }

    @Test
    void invalidAndExpiredReservationsCannotStartNewAttempt() {
        jdbc.update("""
                UPDATE stock_reservations
                SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE order_id=?
                """, orderId);
        assertThrows(PaymentAttemptConflictException.class,
                () -> defaultService.submit(userId, orderId, "attempt-expired-001"));
        assertEquals(0, attemptCount());
    }

    @Test
    void afterCancellationNewKeyIsBlockedButOldKeyIsReadable() {
        var first = defaultService.submit(userId, orderId, "attempt-cancel-001");
        orderLifecycle.cancelOwned(userId, orderId);
        assertThrows(PaymentAttemptConflictException.class,
                () -> defaultService.submit(userId, orderId, "attempt-cancel-002"));
        assertEquals(first.attempt(),
                defaultService.submit(userId, orderId, "attempt-cancel-001").attempt());
        assertEquals(1, attemptCount());
        assertEquals(5, stock());
    }

    @Test
    void rollbackOfClaimDoesNotLeaveGhostAttempt() {
        String keyHash = "A".repeat(64);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            repository.claim(userId, orderId, keyHash);
            assertEquals(1, attemptCount());
            tx.setRollbackOnly();
        });
        assertEquals(0, attemptCount());
    }

    private int attemptCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_attempts WHERE order_id=?
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

    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id=?",
                Integer.class, productId);
    }

    @AfterEach
    void cleanup() {
        if (userId < 1) return;
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
