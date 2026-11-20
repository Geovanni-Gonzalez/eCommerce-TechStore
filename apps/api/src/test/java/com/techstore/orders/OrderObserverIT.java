package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.events.OrderCreatedEvent;
import com.techstore.orders.events.OrderEventPublisher;
import com.techstore.orders.service.CheckoutFacade;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Las pruebas NO son @Transactional: deben observar commits reales,
 * no el rollback automático de la transacción de pruebas.
 */
@SpringBootTest
@ActiveProfiles("integration")
class OrderObserverIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;
    @Autowired OrderEventPublisher events;
    @Autowired PlatformTransactionManager transactions;

    private long userId;
    private long productId;

    @BeforeEach
    void setUp() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        userId = jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Observer prueba', ?, ?, 'CLIENTE') RETURNING id
                """, Long.class, "it.observer." + suffix + "@techstore.test",
                "$2a$12$" + "A".repeat(53));
        productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Mouse observer', 1200.00, 5, TRUE) RETURNING id
                """, Long.class, "OBS-" + suffix);
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 2));
    }

    @Test
    void aCommittedOrderCreatesOneVisibleAuditRecord() {
        Order order = checkout.confirm(userId, address());
        assertEquals(1, auditCount(order.id()));
        assertEquals("ORDER_CREATED", jdbc.queryForObject("""
                SELECT event_type FROM order_event_audit WHERE order_id = ?
                """, String.class, order.id()));
        assertEquals(0, order.totals().totalCrc().compareTo(
                jdbc.queryForObject("""
                        SELECT total_crc FROM order_event_audit WHERE order_id = ?
                        """, BigDecimal.class, order.id())));
        assertEquals(3, stock());
        assertTrue(carts.findItems(userId).isEmpty());
    }

    @Test
    void aRolledBackOrderNeverProducesAnAuditRecord() {
        var tx = new TransactionTemplate(transactions);
        Long revertedOrderId = tx.execute(status -> {
            Order order = checkout.confirm(userId, address());
            status.setRollbackOnly();
            return order.id();
        });
        assertNotNull(revertedOrderId);
        assertEquals(0, auditCount(revertedOrderId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id=?", Integer.class, userId));
        assertEquals(5, stock());
        assertEquals(2, carts.findItems(userId).getFirst().quantity());
    }

    @Test
    void replayedEventDoesNotDuplicateAudit() {
        Order order = checkout.confirm(userId, address());
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                events.orderCreated(new OrderCreatedEvent(
                        order.id(), userId, order.totals().totalCrc())));
        assertEquals(1, auditCount(order.id()));
    }

    private int auditCount(long orderId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_event_audit WHERE order_id = ?
                """, Integer.class, orderId);
    }

    private int stock() {
        return jdbc.queryForObject(
                "SELECT stock FROM products WHERE id=?", Integer.class, productId);
    }

    private static ShippingAddress address() {
        return new ShippingAddress("San José", "Central", "Carmen",
                "Dirección de prueba de observador", "Ana Pérez", "8888-1111");
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
