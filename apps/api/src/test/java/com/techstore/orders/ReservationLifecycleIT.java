package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.inventory.CheckoutStockRepository;
import com.techstore.orders.inventory.OrderReservationLifecycleRepository;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.orders.service.OrderNotCancellableException;
import com.techstore.orders.service.OrderNotFoundException;
import com.techstore.orders.service.OrderReservationService;
import java.util.UUID;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL real: sin @Transactional sobre la clase para comprobar commits. */
@SpringBootTest
@ActiveProfiles("integration")
class ReservationLifecycleIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;
    @Autowired CheckoutStockRepository stockRepository;
    @Autowired OrderReservationLifecycleRepository reservations;
    @Autowired OrderReservationService lifecycle;
    @Autowired PlatformTransactionManager transactions;

    private long userId;
    private long productId;

    @BeforeEach
    void setup() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        userId = jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Reserva prueba', ?, ?, 'CLIENTE') RETURNING id
                """, Long.class, "it.reserve." + suffix + "@techstore.test",
                "$2a$12$" + "A".repeat(53));
        productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Teclado reserva', 1500.00, 5, TRUE) RETURNING id
                """, Long.class, "RES-" + suffix);
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 2));
    }

    @Test
    void cancellingPendingRestoresStockOnceAndKeepsCreationAudit() {
        Order order = createOrder();
        assertEquals(3, currentStock());
        assertEquals(1, auditCount(order.id()));
        lifecycle.cancelOwned(userId, order.id());
        lifecycle.cancelOwned(userId, order.id());
        assertEquals(5, currentStock());
        assertEquals("CANCELLED", orderStatus(order.id()));
        assertEquals("RELEASED", reservationStatus(order.id()));
        assertEquals(1, auditCount(order.id()));
        assertFalse(lifecycle.expireOne(order.id()));
    }

    @Test
    void expirySkipsFutureReservationsThenCancelsDueOnes() {
        Order order = createOrder();
        assertFalse(lifecycle.expireOne(order.id()));
        assertEquals(3, currentStock());
        assertFalse(reservations.findExpiredOrders(100).contains(order.id()));
        jdbc.update("""
                UPDATE stock_reservations
                SET expires_at=CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE order_id=?
                """, order.id());
        assertTrue(reservations.findExpiredOrders(100).contains(order.id()));
        assertTrue(lifecycle.expireOne(order.id()));
        assertFalse(lifecycle.expireOne(order.id()));
        assertEquals(5, currentStock());
        assertEquals("CANCELLED", orderStatus(order.id()));
        assertEquals("RELEASED", reservationStatus(order.id()));
    }

    @Test
    void enclosingTransactionRollbackRevertsCancellationAndStock() {
        Order order = createOrder();
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            lifecycle.cancelOwned(userId, order.id());
            assertEquals(5, currentStock());
            status.setRollbackOnly();
        });
        assertEquals(3, currentStock());
        assertEquals("PENDING", orderStatus(order.id()));
        assertEquals("RESERVED", reservationStatus(order.id()));
    }

    @Test
    void concurrentCancellationCannotReplenishTwice() throws Exception {
        Order order = createOrder();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var operation = (java.util.concurrent.Callable<Void>) () -> {
                ready.countDown();
                assertTrue(go.await(10, TimeUnit.SECONDS));
                lifecycle.cancelOwned(userId, order.id());
                return null;
            };
            var one = pool.submit(operation);
            var two = pool.submit(operation);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }
        assertEquals(5, currentStock());
        assertEquals("RELEASED", reservationStatus(order.id()));
    }

    @Test
    void anotherUserCannotCancelOrSeeExistenceOfTheOrder() {
        Order order = createOrder();
        assertThrows(OrderNotFoundException.class,
                () -> lifecycle.cancelOwned(userId + 10_000, order.id()));
        assertThrows(OrderNotFoundException.class,
                () -> lifecycle.cancelOwned(userId, order.id() + 10_000));
        assertEquals(3, currentStock());
        assertEquals("PENDING", orderStatus(order.id()));
    }

    @Test
    void confirmedOrderCannotBeCancelledOrHaveItsStockReturned() {
        Order order = createOrder();
        stockRepository.consumeReservations(order.id());
        jdbc.update("UPDATE orders SET status='CONFIRMED' WHERE id=?", order.id());
        assertThrows(OrderNotCancellableException.class,
                () -> lifecycle.cancelOwned(userId, order.id()));
        assertFalse(lifecycle.expireOne(order.id()));
        assertEquals(3, currentStock());
        assertEquals("CONSUMED", reservationStatus(order.id()));
    }

    private Order createOrder() {
        return checkout.confirm(userId, new ShippingAddress(
                "San José", "Central", "Carmen",
                "Casa frente al parque central", "Ana Pérez", "8888-1111"));
    }

    private String orderStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id);
    }

    private String reservationStatus(long id) {
        return jdbc.queryForObject(
                "SELECT status FROM stock_reservations WHERE order_id=?",
                String.class, id);
    }

    private int currentStock() {
        return jdbc.queryForObject(
                "SELECT stock FROM products WHERE id=?", Integer.class, productId);
    }

    private int auditCount(long id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_event_audit WHERE order_id=?",
                Integer.class, id);
    }

    @AfterEach
    void cleanup() {
        if (userId < 1) return;
        jdbc.update("""
                DELETE FROM order_event_audit
                WHERE order_id IN (SELECT id FROM orders WHERE user_id=?)
                """, userId);
        jdbc.update("""
                DELETE FROM stock_reservations
                WHERE order_id IN (SELECT id FROM orders WHERE user_id=?)
                """, userId);
        jdbc.update("""
                DELETE FROM order_items
                WHERE order_id IN (SELECT id FROM orders WHERE user_id=?)
                """, userId);
        jdbc.update("DELETE FROM orders WHERE user_id=?", userId);
        jdbc.update("""
                DELETE FROM cart_items
                WHERE cart_id IN (SELECT id FROM carts WHERE user_id=?)
                """, userId);
        jdbc.update("DELETE FROM carts WHERE user_id=?", userId);
        if (productId > 0) jdbc.update("DELETE FROM products WHERE id=?", productId);
        jdbc.update("DELETE FROM users WHERE id=?", userId);
    }
}
