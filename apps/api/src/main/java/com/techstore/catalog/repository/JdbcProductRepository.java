package com.techstore.catalog.repository;

import com.techstore.catalog.domain.CatalogFacet;
import com.techstore.catalog.domain.Product;
import com.techstore.catalog.domain.ProductSearchCriteria;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Catálogo público JDBC: búsqueda Full Text Search y filtros parametrizados.
 * La expresión tsvector coincide con el índice GIN parcial de Flyway V16.
 */
@Repository
public class JdbcProductRepository implements ProductRepository {
    private static final String ACTIVE_FROM = """
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE p.active = TRUE
            """;

    private static final String SELECT_ACTIVE = """
            SELECT p.id, p.category_id, p.sku, p.name, p.description,
                   p.price_crc, p.stock, p.active, p.brand,
                   c.name AS category_name
            """ + ACTIVE_FROM;

    private static final String SEARCH_VECTOR = """
            to_tsvector('simple', coalesce(p.name, '') || ' ' ||
                coalesce(p.description, '') || ' ' || p.sku)
            """;
    private static final RowMapper<Product> PRODUCT_MAPPER = JdbcProductRepository::mapProduct;
    private final JdbcTemplate jdbcTemplate;

    public JdbcProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Product> findActivePage(int limit, long offset) {
        return jdbcTemplate.query(SELECT_ACTIVE + " ORDER BY p.id ASC LIMIT ? OFFSET ?",
                PRODUCT_MAPPER, limit, offset);
    }

    @Override
    public Optional<Product> findActiveById(long id) {
        List<Product> products = jdbcTemplate.query(SELECT_ACTIVE + " AND p.id = ?",
                PRODUCT_MAPPER, id);
        return products.stream().findFirst();
    }

    @Override
    public long countActive() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE active = TRUE", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public List<Product> findActiveFilteredPage(
            ProductSearchCriteria criteria, int limit, long offset) {
        Query query = buildQuery(criteria);
        List<Object> args = new ArrayList<>(query.parameters());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                SELECT_ACTIVE + query.conditions() + " ORDER BY p.id ASC LIMIT ? OFFSET ?",
                PRODUCT_MAPPER, args.toArray());
    }

    @Override
    public long countActiveFiltered(ProductSearchCriteria criteria) {
        Query query = buildQuery(criteria);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + ACTIVE_FROM + query.conditions(),
                Long.class, query.parameters().toArray());
        return count == null ? 0L : count;
    }

    @Override
    public List<CatalogFacet> findAvailableCategories() {
        return jdbcTemplate.query("""
                SELECT c.id, c.name FROM categories c
                WHERE EXISTS (
                    SELECT 1 FROM products p
                    WHERE p.active = TRUE AND p.category_id = c.id
                )
                ORDER BY c.name ASC, c.id ASC
                """, (rs, row) -> new CatalogFacet(rs.getLong("id"), rs.getString("name")));
    }

    @Override
    public List<String> findAvailableBrands() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT brand FROM products
                WHERE active = TRUE AND brand IS NOT NULL AND btrim(brand) <> ''
                ORDER BY brand ASC
                """, String.class);
    }

    private static Query buildQuery(ProductSearchCriteria filters) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (filters.query() != null) {
            where.append(" AND ").append(SEARCH_VECTOR)
                    .append(" @@ websearch_to_tsquery('simple', ?)");
            params.add(filters.query());
        }
        if (filters.categoryId() != null) {
            where.append(" AND p.category_id = ?");
            params.add(filters.categoryId());
        }
        if (filters.brand() != null) {
            where.append(" AND lower(p.brand) = lower(?)");
            params.add(filters.brand());
        }
        if (filters.minPriceCrc() != null) {
            where.append(" AND p.price_crc >= ?");
            params.add(filters.minPriceCrc());
        }
        if (filters.maxPriceCrc() != null) {
            where.append(" AND p.price_crc <= ?");
            params.add(filters.maxPriceCrc());
        }
        return new Query(where.toString(), List.copyOf(params));
    }

    private static Product mapProduct(ResultSet rs, int rowNum) throws SQLException {
        long categoryValue = rs.getLong("category_id");
        Long categoryId = rs.wasNull() ? null : categoryValue;
        return new Product(
                rs.getLong("id"), categoryId, rs.getString("sku"),
                rs.getString("name"), rs.getString("description"),
                rs.getBigDecimal("price_crc"), rs.getInt("stock"),
                rs.getBoolean("active"), rs.getString("brand"),
                rs.getString("category_name"));
    }
    private record Query(String conditions, List<Object> parameters) { }
}
