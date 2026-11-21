package com.techstore.orders.query;

import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.domain.OrderTotals;
import com.techstore.orders.domain.PaymentMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * El propietario se filtra en PostgreSQL, no se filtra en Java tras
 * recuperar pedidos ajenos. Solo resumen: nunca cargar direcciones al listar.
 */
@Repository
public class JdbcOrderListRepository implements OrderListRepository {
    private static final String COLUMNS = """
            id, status, payment_mode, subtotal_crc, shipping_crc,
            tax_crc, total_crc, created_at
            """;
    private static final RowMapper<OrderSummary> CLIENT_ROW =
            (rs, row) -> mapOrder(rs);
    private static final RowMapper<AdminOrderSummary> ADMIN_ROW =
            (rs, row) -> new AdminOrderSummary(
                    rs.getLong("user_id"), mapOrder(rs));

    private final JdbcTemplate jdbc;

    public JdbcOrderListRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public OrderPage<OrderSummary> listForOwner(long userId, int limit, Long beforeId) {
        if (userId < 1) {
            throw new IllegalArgumentException("Usuario inválido");
        }
        String sql = "SELECT " + COLUMNS + " FROM orders WHERE user_id=? "
                + (beforeId == null ? "" : "AND id < ? ")
                + "ORDER BY id DESC LIMIT ?";
        List<OrderSummary> rows = beforeId == null
                ? jdbc.query(sql, CLIENT_ROW, userId, limit + 1)
                : jdbc.query(sql, CLIENT_ROW, userId, beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<OrderSummary> visible = rows.subList(0, Math.min(limit, rows.size()));
        return new OrderPage<>(visible, more,
                more ? visible.getLast().id() : null);
    }

    @Override
    public OrderPage<AdminOrderSummary> listForAdmin(int limit, Long beforeId) {
        String sql = "SELECT user_id, " + COLUMNS + " FROM orders "
                + (beforeId == null ? "" : "WHERE id < ? ")
                + "ORDER BY id DESC LIMIT ?";
        List<AdminOrderSummary> rows = beforeId == null
                ? jdbc.query(sql, ADMIN_ROW, limit + 1)
                : jdbc.query(sql, ADMIN_ROW, beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<AdminOrderSummary> visible = rows.subList(0, Math.min(limit, rows.size()));
        return new OrderPage<>(visible, more,
                more ? visible.getLast().order().id() : null);
    }

    private static OrderSummary mapOrder(ResultSet rs) throws SQLException {
        return new OrderSummary(rs.getLong("id"),
                OrderStatus.valueOf(rs.getString("status")),
                PaymentMode.valueOf(rs.getString("payment_mode")),
                new OrderTotals(rs.getBigDecimal("subtotal_crc"),
                        rs.getBigDecimal("shipping_crc"),
                        rs.getBigDecimal("tax_crc"),
                        rs.getBigDecimal("total_crc")),
                rs.getObject("created_at", java.time.OffsetDateTime.class));
    }
}
