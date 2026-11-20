package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.events.OrderEventPublisher;
import com.techstore.orders.service.CheckoutFacade;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Al fallar la etapa final (publicar evento), debe revertirse TODO:
 * order, order_items, stock_reservations, descuento de stock y vaciado carrito.
 * Contexto distinto con publisher que falla a propósito.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Import(CheckoutRollbackIT.FailingPublisher.class)
class CheckoutRollbackIT {
    @TestConfiguration
    static class FailingPublisher {
        @Bean @Primary
        OrderEventPublisher failingCheckoutEventPublisher() {
            return event -> { throw new IllegalStateException("Fallo simulado al publicar"); };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;

    private long userId;
    private long productId;

    @BeforeEach
    void setup() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class));
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        userId = jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Prueba rollback', ?, ?, 'CLIENTE') RETURNING id
                """, Long.class, "it.rollback." + suffix + "@techstore.test",
                "$2a$12$" + "A".repeat(53));
        productId = jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Mouse rollback', 2500.00, 3, TRUE) RETURNING id
                """, Long.class, "ROLLBACK-" + suffix);
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 2));
    }

    @Test
    void failureAfterReservationRestoresEntireTransaction() {
        var address = new ShippingAddress("San José", "Central", "Carmen",
                "Frente a la plaza, casa azul", "Ana Pérez", "8888-1111");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> checkout.confirm(userId, address));
        assertEquals("Fallo simulado al publicar", ex.getMessage());

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id=?",
                Integer.class, userId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_status_history
                WHERE order_id IN (SELECT id FROM orders WHERE user_id=?)
                """, Integer.class, userId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM stock_reservations sr
                JOIN products p ON p.id=sr.product_id
                WHERE p.id=?
                """, Integer.class, productId));
        assertEquals(3, jdbc.queryForObject(
                "SELECT stock FROM products WHERE id=?",
                Integer.class, productId));
        assertEquals(2, carts.findItems(userId).getFirst().quantity());
    }

    @AfterEach
    void cleanup() {
        if (userId < 1) return;
        jdbc.update("""
                DELETE FROM cart_items WHERE cart_id IN (
                    SELECT id FROM carts WHERE user_id = ?)
                """, userId);
        jdbc.update("DELETE FROM carts WHERE user_id=?", userId);
        if (productId > 0) jdbc.update("DELETE FROM products WHERE id=?", productId);
        jdbc.update("DELETE FROM users WHERE id=?", userId);
    }
}
