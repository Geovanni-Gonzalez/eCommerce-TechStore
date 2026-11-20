package com.techstore.orders.inventory;

import com.techstore.cart.domain.CartLine;
import com.techstore.orders.pricing.EmptyCheckoutCartException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locks de filas con orden estable y UPDATE condicional: incluso si dos clientes
 * compiten por la última unidad, nunca se produce stock negativo.
 */
@Repository
public final class JdbcCheckoutStockRepository implements CheckoutStockRepository {
    private final JdbcTemplate jdbc;

    public JdbcCheckoutStockRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void lockCustomerCart(long userId) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM carts WHERE user_id = ? FOR UPDATE
                """, (rs, row) -> rs.getLong(1), userId);
        if (ids.isEmpty()) {
            throw new EmptyCheckoutCartException();
        }
        // Protege ediciones concurrentes de líneas ya existentes.
        jdbc.query("""
                SELECT id FROM cart_items WHERE cart_id = ?
                ORDER BY product_id FOR UPDATE
                """, (rs, row) -> rs.getLong(1), ids.getFirst());
    }

    @Override
    public void lockProducts(List<Long> productIds) {
        Objects.requireNonNull(productIds, "productIds");
        // Bloqueo individual en orden ascendente => reduce interbloqueos.
        for (Long id : productIds.stream().distinct().sorted().toList()) {
            if (id == null || id < 1 || jdbc.query("""
                    SELECT id FROM products WHERE id = ? FOR UPDATE
                    """, (rs, row) -> rs.getLong(1), id).isEmpty()) {
                throw new InsufficientStockException();
            }
        }
    }

    @Override
    public void reserveForOrder(long orderId, List<CartLine> lines) {
        if (orderId < 1 || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("La reserva requiere pedido y productos");
        }
        for (CartLine line : lines) {
            if (line.quantity() < 1 || line.quantity() > 99
                    || jdbc.update("""
                            UPDATE products SET stock = stock - ?
                            WHERE id = ? AND active AND stock >= ?
                            """, line.quantity(), line.productId(),
                            line.quantity()) != 1) {
                throw new InsufficientStockException();
            }
            jdbc.update("""
                    INSERT INTO stock_reservations(order_id,product_id,quantity)
                    VALUES (?,?,?)
                    """, orderId, line.productId(), line.quantity());
        }
    }

    @Override
    public void releaseReservations(long orderId) {
        // Serializa liberación/consumo entre procesos y garantiza transiciones
        // como máximo una vez. El consumidor deberá ejecutar @Transactional.
        List<Reservation> pending = jdbc.query("""
                SELECT product_id,quantity
                FROM stock_reservations
                WHERE order_id = ? AND status = 'RESERVED'
                ORDER BY product_id FOR UPDATE
                """, (rs, row) -> new Reservation(
                rs.getLong("product_id"), rs.getInt("quantity")), orderId);
        for (Reservation line : pending) {
            int affected = jdbc.update("""
                    UPDATE stock_reservations
                    SET status = 'RELEASED', updated_at = CURRENT_TIMESTAMP
                    WHERE order_id=? AND product_id=? AND status='RESERVED'
                    """, orderId, line.productId());
            if (affected != 1) {
                throw new IllegalStateException("Cambio concurrente de reserva");
            }
            jdbc.update("UPDATE products SET stock=stock+? WHERE id=?",
                    line.quantity(), line.productId());
        }
    }

    @Override
    public void consumeReservations(long orderId) {
        jdbc.update("""
                UPDATE stock_reservations
                SET status = 'CONSUMED', updated_at = CURRENT_TIMESTAMP
                WHERE order_id=? AND status='RESERVED'
                """, orderId);
    }

    private record Reservation(long productId, int quantity) { }
}
