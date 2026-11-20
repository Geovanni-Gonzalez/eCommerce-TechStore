package com.techstore.cart.repository;

import com.techstore.cart.domain.CartLine;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Todos los WHERE filtran el propietario autenticado; no hay acceso a un cartId
 * arbitrario. Los UPSERTs son atómicos y respetan stock actual y cantidad máxima.
 */
@Repository
public class JdbcCartRepository implements CartRepository {

    private static final RowMapper<CartLine> LINE_MAPPER =
            JdbcCartRepository::mapLine;

    private final JdbcTemplate jdbc;

    public JdbcCartRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public List<CartLine> findItems(long userId) {
        return jdbc.query("""
                SELECT p.id AS product_id, p.sku, p.name, p.price_crc,
                       ci.quantity, p.stock, p.active
                FROM carts c
                JOIN cart_items ci ON ci.cart_id = c.id
                JOIN products p ON p.id = ci.product_id
                WHERE c.user_id = ?
                ORDER BY p.id
                """, LINE_MAPPER, userId);
    }

    @Override
    public boolean containsProduct(long userId, long productId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM carts c JOIN cart_items ci ON ci.cart_id = c.id
                WHERE c.user_id = ? AND ci.product_id = ?
                """, Long.class, userId, productId);
        return count != null && count > 0;
    }

    @Override
    public void ensureCart(long userId) {
        jdbc.update("""
                INSERT INTO carts (user_id) VALUES (?)
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
    }

    @Override
    public boolean incrementProduct(long userId, long productId, int quantity) {
        // El primer INSERT y el UPDATE concurrente comprueban disponibilidad
        // en SQL; una lectura previa del servicio no es suficiente.
        return jdbc.update("""
                INSERT INTO cart_items (cart_id, product_id, quantity)
                SELECT c.id, p.id, ?
                FROM carts c JOIN products p ON p.id = ?
                WHERE c.user_id = ? AND p.active AND p.stock >= ?
                ON CONFLICT (cart_id, product_id) DO UPDATE
                SET quantity = cart_items.quantity + EXCLUDED.quantity,
                    updated_at = CURRENT_TIMESTAMP
                WHERE cart_items.quantity + EXCLUDED.quantity <= 99
                  AND EXISTS (
                    SELECT 1 FROM products p
                    WHERE p.id = EXCLUDED.product_id AND p.active
                    AND p.stock >= cart_items.quantity + EXCLUDED.quantity
                  )
                """, quantity, productId, userId, quantity) == 1;
    }

    @Override
    public boolean setQuantity(long userId, long productId, int quantity) {
        return jdbc.update("""
                UPDATE cart_items ci
                SET quantity = ?, updated_at = CURRENT_TIMESTAMP
                FROM carts c, products p
                WHERE ci.cart_id = c.id AND c.user_id = ?
                  AND ci.product_id = ? AND p.id = ci.product_id
                  AND p.active AND p.stock >= ?
                """, quantity, userId, productId, quantity) == 1;
    }

    @Override
    public boolean removeProduct(long userId, long productId) {
        return jdbc.update("""
                DELETE FROM cart_items ci USING carts c
                WHERE ci.cart_id = c.id AND c.user_id = ?
                  AND ci.product_id = ?
                """, userId, productId) == 1;
    }

    @Override
    public void clear(long userId) {
        jdbc.update("""
                DELETE FROM cart_items
                WHERE cart_id IN (SELECT id FROM carts WHERE user_id = ?)
                """, userId);
    }

    private static CartLine mapLine(ResultSet rs, int rowNum)
            throws SQLException {
        return new CartLine(
                rs.getLong("product_id"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getBigDecimal("price_crc"),
                rs.getInt("quantity"),
                rs.getInt("stock"),
                rs.getBoolean("active"));
    }
}
