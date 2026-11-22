package com.techstore.reviews;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas parametrizadas y filtradas por el usuario del Bearer opaco. */
@Repository
public class JdbcProductReviewRepository {
    private final JdbcTemplate jdbc;

    public JdbcProductReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public boolean productExists(long productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM products WHERE id=?)",
                Boolean.class, productId));
    }

    public boolean hasPhysicalDelivery(long userId, long productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM orders o
                    JOIN order_items oi ON oi.order_id=o.id
                    JOIN shipments s ON s.order_id=o.id
                    JOIN shipment_status_history h ON h.shipment_id=s.id
                    WHERE o.user_id=? AND oi.product_id=?
                      AND o.status='CONFIRMED'
                      AND s.fulfillment_method='MANUAL'
                      AND s.status='DELIVERED'
                      AND h.from_status='IN_TRANSIT'
                      AND h.to_status='DELIVERED'
                      AND h.reason='MANUAL_DELIVERY'
                )
                """, Boolean.class, userId, productId));
    }

    public boolean hasActiveReview(long userId, long productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM product_reviews
                    WHERE user_id=? AND product_id=? AND active
                )
                """, Boolean.class, userId, productId));
    }

    public Optional<ProductReview> findMine(long userId,long productId) {
        return jdbc.query("""
                SELECT id,product_id,rating,comment,created_at
                FROM product_reviews
                WHERE user_id=? AND product_id=? AND active
                """,(rs,row)->new ProductReview(
                    rs.getLong("id"),rs.getLong("product_id"),
                    rs.getInt("rating"),rs.getString("comment"),
                    rs.getObject("created_at",OffsetDateTime.class)),
                userId,productId).stream().findFirst();
    }

    public ProductReview insert(long userId,long productId,ReviewInput input) {
        List<ProductReview> rows = jdbc.query("""
                INSERT INTO product_reviews (user_id,product_id,rating,comment)
                VALUES (?,?,?,?)
                RETURNING id,product_id,rating,comment,created_at
                """,(rs,row)->new ProductReview(
                    rs.getLong("id"),rs.getLong("product_id"),
                    rs.getInt("rating"),rs.getString("comment"),
                    rs.getObject("created_at",OffsetDateTime.class)),
                userId,productId,input.rating(),input.comment());
        if (rows.size()!=1) throw new IllegalStateException("Fallo al registrar reseña");
        return rows.getFirst();
    }
}
