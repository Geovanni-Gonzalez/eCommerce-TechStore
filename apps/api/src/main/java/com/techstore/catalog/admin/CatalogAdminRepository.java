package com.techstore.catalog.admin;

import com.techstore.catalog.dto.CategoryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC ADMIN: SQL parametrizado. No expone operaciones al catálogo público.
 * El stock se lee pero no se modifica: reserva y liberación corresponden
 * a checkout y al futuro módulo BKL-026/046.
 */
@Repository
public class CatalogAdminRepository {
    private static final String PRODUCT_SELECT = """
            SELECT p.id, p.sku, p.name, p.description, p.category_id,
                   c.name AS category_name, p.brand, p.price_crc,
                   p.stock, p.active
            FROM products p LEFT JOIN categories c ON c.id=p.category_id
            """;
    private final JdbcTemplate jdbc;

    public CatalogAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminProduct> listProducts(int limit, long offset) {
        return jdbc.query(PRODUCT_SELECT + """
                ORDER BY p.id ASC LIMIT ? OFFSET ?
                """, CatalogAdminRepository::product, limit, offset);
    }

    public long countProducts() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);
        return count == null ? 0L : count;
    }

    public AdminProduct findProduct(long id) {
        return jdbc.query(PRODUCT_SELECT + " WHERE p.id=?",
                CatalogAdminRepository::product, id).stream().findFirst().orElse(null);
    }

    public long createProduct(CatalogAdminInput.ProductWrite item) {
        Long id = jdbc.queryForObject("""
                INSERT INTO products(sku,name,description,category_id,brand,price_crc,stock,active)
                VALUES (?,?,?,?,?,?,0,TRUE) RETURNING id
                """, Long.class, item.sku(), item.name(), item.description(),
                item.categoryId(), item.brand(), item.priceCrc());
        if (id == null) throw new IllegalStateException("Sin ID para producto nuevo");
        return id;
    }

    public int updateProduct(long id, CatalogAdminInput.ProductWrite item) {
        return jdbc.update("""
                UPDATE products SET sku=?,name=?,description=?,category_id=?,
                    brand=?,price_crc=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, item.sku(),item.name(),item.description(),item.categoryId(),
                item.brand(),item.priceCrc(),id);
    }

    public int setProductActive(long id, boolean active) {
        return jdbc.update("""
                UPDATE products SET active=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, active,id);
    }

    public List<CategoryResponse> categories() {
        return jdbc.query("""
                SELECT id,name FROM categories ORDER BY name ASC,id ASC
                """, (rs,index)->new CategoryResponse(
                        rs.getLong("id"),rs.getString("name")));
    }

    public CategoryResponse findCategory(long id) {
        return jdbc.query("""
                SELECT id,name FROM categories WHERE id=?
                """, (rs,index)->new CategoryResponse(
                        rs.getLong("id"),rs.getString("name")), id)
                .stream().findFirst().orElse(null);
    }

    public long createCategory(String name) {
        Long id = jdbc.queryForObject("""
                INSERT INTO categories(name) VALUES (?) RETURNING id
                """, Long.class,name);
        if (id == null) throw new IllegalStateException("Sin ID para categoría");
        return id;
    }

    public int renameCategory(long id, String name) {
        return jdbc.update("UPDATE categories SET name=? WHERE id=?",name,id);
    }

    public int deleteCategory(long id) {
        // FK RESTRICT evita eliminar una categoría con cualquier producto,
        // incluso inactivo o presente en el histórico.
        return jdbc.update("DELETE FROM categories WHERE id=?",id);
    }

    private static AdminProduct product(ResultSet rs, int row) throws SQLException {
        long category = rs.getLong("category_id");
        Long categoryId = rs.wasNull()?null:category;
        return new AdminProduct(
                rs.getLong("id"),rs.getString("sku"),rs.getString("name"),
                rs.getString("description"),categoryId,rs.getString("category_name"),
                rs.getString("brand"),rs.getBigDecimal("price_crc"),
                rs.getInt("stock"),rs.getBoolean("active"));
    }
}
