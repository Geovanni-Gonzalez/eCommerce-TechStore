package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.OrderDraft;
import com.techstore.orders.domain.OrderItemSnapshot;
import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.domain.OrderTotals;
import com.techstore.orders.domain.PaymentMode;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL real, Flyway V6 y rollback sobre la base aislada techstore_test. */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class OrderRepositoryIT {

    private static final String HASH = "$2a$12$" + "A".repeat(53);

    @Autowired JdbcTemplate jdbc;
    @Autowired OrderRepository orders;

    @BeforeEach
    void requireIsolatedTestDatabase() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class),
                "No ejecutar pruebas de escritura fuera de techstore_test");
    }

    @Test
    void createsOrderAndTwoLineSnapshotsWithSimulatedPayment() {
        long user = user("it.order.first@techstore.test");
        long mouse = product("ORD-MOUSE", "Mouse", "500.25");
        long cable = product("ORD-CABLE", "Cable", "500.00");
        var draft = draft(user, List.of(
                new OrderItemSnapshot(mouse, "Mouse", 2, new BigDecimal("500.25")),
                new OrderItemSnapshot(cable, "Cable", 1, new BigDecimal("500.00"))));

        Order saved = orders.saveDraft(draft);

        assertTrue(saved.id() > 0);
        assertEquals(user, saved.userId());
        assertEquals(OrderStatus.PENDING, saved.status());
        assertEquals(PaymentMode.SIMULATED, saved.paymentMode());
        assertNotNull(saved.createdAt());
        assertEquals("San José", saved.shippingAddress().province());
        assertEquals("Ana Pérez", saved.shippingAddress().recipientName());
        assertEquals(2, saved.items().size());
        assertEquals("Mouse", saved.items().getFirst().name());
        assertEquals(new BigDecimal("1000.50"), saved.items().getFirst().lineTotalCrc());
        assertEquals(new BigDecimal("1900.55"), saved.totals().totalCrc());

        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ? AND user_id = ?",
                Long.class, saved.id(), user));
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?",
                Long.class, saved.id()));
    }

    @Test
    void catalogChangesAndProductDeletionDoNotRewriteHistoricalSnapshots() {
        long user = user("it.order.history@techstore.test");
        long product = product("ORD-HIST", "Nombre original", "500.25");
        var saved = orders.saveDraft(draft(user, List.of(
                new OrderItemSnapshot(product, "Nombre original", 2,
                        new BigDecimal("500.25")))));

        jdbc.update("""
                UPDATE products SET name = ?, price_crc = ?, active = FALSE
                WHERE id = ?
                """, "Nombre renovado", new BigDecimal("900.00"), product);
        jdbc.update("DELETE FROM products WHERE id = ?", product);

        Order historic = orders.findByIdForUser(user, saved.id()).orElseThrow();
        assertEquals("Nombre original", historic.items().getFirst().name());
        assertEquals(new BigDecimal("500.25"),
                historic.items().getFirst().unitPriceCrc());
        assertEquals(new BigDecimal("1000.50"),
                historic.items().getFirst().lineTotalCrc());
        assertEquals(product, historic.items().getFirst().productId());
    }

    @Test
    void ordersOfAnotherUserAreNotVisible() {
        long alice = user("it.order.alice@techstore.test");
        long bob = user("it.order.bob@techstore.test");
        long product = product("ORD-OWNER", "Monitor", "500.25");
        Order created = orders.saveDraft(draft(alice, List.of(
                new OrderItemSnapshot(product, "Monitor", 2,
                        new BigDecimal("500.25")))));

        assertTrue(orders.findByIdForUser(bob, created.id()).isEmpty());
        assertTrue(orders.findByIdForUser(alice, -5).isEmpty());
        assertTrue(orders.findByIdForUser(0, created.id()).isEmpty());
        assertEquals(created, orders.findByIdForUser(alice, created.id()).orElseThrow());
    }

    @Test
    void databaseRejectsInvalidStatus() {
        long user = user("it.order.status@techstore.test");
        long product = product("ORD-STATUS", "Mouse", "500.25");
        Order saved = orders.saveDraft(draft(user, List.of(
                new OrderItemSnapshot(product, "Mouse", 2,
                        new BigDecimal("500.25")))));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE orders SET status = 'FRAUD' WHERE id = ?", saved.id()));
    }

    @Test
    void databaseRejectsNonSimulatedPayment() {
        long user = user("it.order.payment@techstore.test");
        long product = product("ORD-PAY", "Mouse", "500.25");
        Order saved = orders.saveDraft(draft(user, List.of(
                new OrderItemSnapshot(product, "Mouse", 2,
                        new BigDecimal("500.25")))));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE orders SET payment_mode = 'REAL' WHERE id = ?", saved.id()));
    }

    @Test
    void databaseRejectsTamperingWithSnapshotLineAmounts() {
        long user = user("it.order.amount@techstore.test");
        long product = product("ORD-AMOUNT", "Mouse", "500.25");
        Order saved = orders.saveDraft(draft(user, List.of(
                new OrderItemSnapshot(product, "Mouse", 2,
                        new BigDecimal("500.25")))));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        UPDATE order_items SET line_total_crc = 1
                        WHERE order_id = ?
                        """, saved.id()));
    }

    @Test
    void databaseRejectsHeaderTotalInconsistentWithComponents() {
        long user = user("it.order.totals@techstore.test");
        long product = product("ORD-TOTAL", "Mouse", "500.25");
        Order saved = orders.saveDraft(draft(user, List.of(
                new OrderItemSnapshot(product, "Mouse", 2,
                        new BigDecimal("500.25")))));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE orders SET total_crc = 1 WHERE id = ?", saved.id()));
    }

    @Test
    void databaseRejectsUnknownOwnerAndPreservesReferentialIntegrity() {
        var draft = draft(Long.MAX_VALUE, List.of(
                new OrderItemSnapshot(123, "Mouse", 2, new BigDecimal("500.25"))));
        assertThrows(DataIntegrityViolationException.class,
                () -> orders.saveDraft(draft));
    }

    private OrderDraft draft(long user, List<OrderItemSnapshot> items) {
        BigDecimal subtotal = items.stream().map(OrderItemSnapshot::lineTotalCrc)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        var totals = new OrderTotals(subtotal, new BigDecimal("250.00"),
                new BigDecimal("150.05"), subtotal.add(new BigDecimal("400.05")));
        return new OrderDraft(user, address(), items, totals);
    }

    private ShippingAddress address() {
        return new ShippingAddress("San José", "Central", "Carmen",
                "Frente al parque, casa azul", "Ana Pérez", "8888-1111");
    }

    private long user(String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (display_name, email, password_hash, role)
                VALUES ('Cliente prueba', ?, ?, 'CLIENTE')
                RETURNING id
                """, Long.class, email, HASH);
    }

    private long product(String sku, String name, String price) {
        return jdbc.queryForObject("""
                INSERT INTO products (sku, name, price_crc, stock, active)
                VALUES (?, ?, ?, 10, TRUE) RETURNING id
                """, Long.class, sku, name, new BigDecimal(price));
    }
}
