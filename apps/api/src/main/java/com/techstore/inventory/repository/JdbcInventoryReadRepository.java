package com.techstore.inventory.repository;

import com.techstore.inventory.domain.InventoryBalance;
import com.techstore.inventory.domain.InventoryMovement;
import com.techstore.inventory.domain.InventoryMovementKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consulta de inventario BKL-025. VIEW y auditoría no exponen endpoints públicos.
 * El futuro controlador ADMIN reutilizará este puerto (BKL-026/BKL-046).
 */
@Repository
public final class JdbcInventoryReadRepository implements InventoryReadRepository {
    private final JdbcTemplate jdbc;

    public JdbcInventoryReadRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<InventoryBalance> findBalance(long productId) {
        if (productId < 1) throw new IllegalArgumentException("ID inválido");
        return jdbc.query("""
                SELECT product_id,physical_stock,reserved_stock,available_stock
                FROM inventory_balances WHERE product_id=?
                """, JdbcInventoryReadRepository::balance, productId)
                .stream().findFirst();
    }

    @Override
    public List<InventoryBalance> listBalances(int limit, long offset) {
        validatePage(limit, offset);
        return jdbc.query("""
                SELECT product_id,physical_stock,reserved_stock,available_stock
                FROM inventory_balances ORDER BY product_id ASC LIMIT ? OFFSET ?
                """, JdbcInventoryReadRepository::balance, limit, offset);
    }

    @Override
    public List<InventoryMovement> findMovements(long productId, int limit, long offset) {
        if (productId < 1) throw new IllegalArgumentException("ID inválido");
        validatePage(limit, offset);
        return jdbc.query("""
                SELECT id,product_id,order_id,kind,quantity,
                       available_delta,reserved_delta,physical_delta,recorded_at
                FROM inventory_movements WHERE product_id=?
                ORDER BY recorded_at DESC,id DESC LIMIT ? OFFSET ?
                """, JdbcInventoryReadRepository::movement, productId, limit, offset);
    }

    private static InventoryBalance balance(ResultSet rs, int row) throws SQLException {
        return new InventoryBalance(
                rs.getLong("product_id"), rs.getLong("physical_stock"),
                rs.getLong("reserved_stock"), rs.getLong("available_stock"));
    }

    private static InventoryMovement movement(ResultSet rs, int row) throws SQLException {
        long order = rs.getLong("order_id");
        Long orderId = rs.wasNull() ? null : order;
        return new InventoryMovement(
                rs.getLong("id"), rs.getLong("product_id"), orderId,
                InventoryMovementKind.valueOf(rs.getString("kind")),
                rs.getLong("quantity"), rs.getLong("available_delta"),
                rs.getLong("reserved_delta"), rs.getLong("physical_delta"),
                rs.getObject("recorded_at", OffsetDateTime.class));
    }

    private static void validatePage(int limit, long offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("Paginación de inventario inválida");
        }
    }
}
