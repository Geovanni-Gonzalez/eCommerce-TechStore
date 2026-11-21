package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.inventory.InsufficientStockException;
import com.techstore.orders.pricing.UnavailableCheckoutItemException;
import com.techstore.orders.service.CheckoutFacade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Concurrencia REAL: dos transacciones JDBC independientes compiten por
 * la última unidad. Nunca ejecutar contra otra base que techstore_test.
 */
@SpringBootTest
@ActiveProfiles("integration")
class CheckoutConcurrencyIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;

    private long alice;
    private long bob;
    private long product;

    @BeforeEach
    void setUp() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        alice = user("it.race.a." + suffix + "@techstore.test");
        bob = user("it.race.b." + suffix + "@techstore.test");
        product = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Última unidad', 1000.00, 1, TRUE) RETURNING id
                """, Long.class, "RACE-" + suffix);
        carts.ensureCart(alice);
        carts.ensureCart(bob);
        assertTrue(carts.incrementProduct(alice, product, 1));
        assertTrue(carts.incrementProduct(bob, product, 1));
    }

    @Test
    void simultaneousCheckoutsNeverOversellOrCreateOrphanReservations() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> attemptAlice = () -> attempt(alice, ready, start);
        Callable<Boolean> attemptBob = () -> attempt(bob, ready, start);

        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = pool.submit(attemptAlice);
            Future<Boolean> second = pool.submit(attemptBob);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int succeeded = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, succeeded, "Solo una de las dos reservas puede ganar");
        }

        assertEquals(0, jdbc.queryForObject(
                "SELECT stock FROM products WHERE id=?", Integer.class, product));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders
                WHERE user_id IN (?, ?)
                """, Integer.class, alice, bob));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_status_history h
                JOIN orders o ON o.id=h.order_id
                WHERE o.user_id IN (?,?) AND h.reason='CREATED'
                """, Integer.class, alice, bob));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM stock_reservations r
                JOIN orders o ON o.id=r.order_id
                WHERE o.user_id IN (?, ?) AND r.product_id=? AND r.status='RESERVED'
                """, Integer.class, alice, bob, product));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM cart_items ci JOIN carts c ON c.id=ci.cart_id
                WHERE c.user_id IN (?, ?)
                """, Integer.class, alice, bob));
    }

    private boolean attempt(long userId, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            checkout.confirm(userId, address());
            return true;
        } catch (UnavailableCheckoutItemException | InsufficientStockException expected) {
            return false;
        }
    }

    private static ShippingAddress address() {
        return new ShippingAddress("San José", "Central", "Carmen",
                "Dirección de prueba concurrencia", "Ana Pérez", "8888-1111");
    }

    private long user(String email) {
        return jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Prueba carrera', ?, ?, 'CLIENTE') RETURNING id
                """, Long.class, email, "$2a$12$" + "A".repeat(53));
    }

    @AfterEach
    void cleanFixtures() {
        if (alice < 1 || bob < 1) return;
        jdbc.update("""
                DELETE FROM order_status_history
                WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))
                """, alice, bob);
        jdbc.update("""
                DELETE FROM order_event_audit
                WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))
                """, alice, bob);
        jdbc.update("""
                DELETE FROM stock_reservations
                WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))
                """, alice, bob);
        jdbc.update("""
                DELETE FROM order_items
                WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))
                """, alice, bob);
        jdbc.update("DELETE FROM orders WHERE user_id IN (?,?)", alice, bob);
        jdbc.update("""
                DELETE FROM cart_items
                WHERE cart_id IN (SELECT id FROM carts WHERE user_id IN (?,?))
                """, alice, bob);
        jdbc.update("DELETE FROM carts WHERE user_id IN (?,?)", alice, bob);
        if (product > 0) jdbc.update("DELETE FROM products WHERE id=?", product);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", alice, bob);
    }
}
