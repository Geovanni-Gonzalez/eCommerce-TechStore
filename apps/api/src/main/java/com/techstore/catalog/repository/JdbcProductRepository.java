package com.techstore.catalog.repository;

import com.techstore.catalog.domain.Product;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Implementación de lectura del catálogo público mediante Spring JDBC.
 *
 * Las consultas filtran productos inactivos y ordenan la paginación por ID.
 * El servicio decide los límites de paginación y el controlador los códigos HTTP.
 */
@Repository
public class JdbcProductRepository implements ProductRepository {

    private static final String SELECT_ACTIVE = """
            SELECT
                id,
                category_id,
                sku,
                name,
                description,
                price_crc,
                stock,
                active
            FROM products
            WHERE active = TRUE
            """;

    private static final RowMapper<Product> PRODUCT_MAPPER =
            JdbcProductRepository::mapProduct;

    private final JdbcTemplate jdbcTemplate;

    public JdbcProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Product> findActivePage(int limit, long offset) {
        String sql = SELECT_ACTIVE + """
                ORDER BY id ASC
                LIMIT ? OFFSET ?
                """;

        return jdbcTemplate.query(sql, PRODUCT_MAPPER, limit, offset);
    }

    @Override
    public Optional<Product> findActiveById(long id) {
        String sql = SELECT_ACTIVE + "AND id = ?";

        List<Product> products = jdbcTemplate.query(sql, PRODUCT_MAPPER, id);
        return products.stream().findFirst();
    }

    @Override
    public long countActive() {
        String sql = """
                SELECT COUNT(*)
                FROM products
                WHERE active = TRUE
                """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private static Product mapProduct(ResultSet rs, int rowNum)
            throws SQLException {
        long categoryValue = rs.getLong("category_id");
        Long categoryId = rs.wasNull() ? null : categoryValue;

        return new Product(
                rs.getLong("id"),
                categoryId,
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price_crc"),
                rs.getInt("stock"),
                rs.getBoolean("active")
        );
    }
}
