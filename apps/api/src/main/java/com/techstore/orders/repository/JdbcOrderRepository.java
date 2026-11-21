package com.techstore.orders.repository;

import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.OrderDraft;
import com.techstore.orders.domain.OrderItemSnapshot;
import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.domain.OrderTotals;
import com.techstore.orders.domain.PaymentMode;
import com.techstore.orders.domain.ShippingAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC del modelo histórico de pedidos.
 *
 * La inserción de cabecera y líneas es atómica gracias a @Transactional.
 * NO calcula precios, reserva stock ni expone rutas públicas: debe invocarse
 * desde el futuro checkout que haga esas validaciones (BKL-032/033).
 */
@Repository
public class JdbcOrderRepository implements OrderRepository {

    private static final RowMapper<OrderHeader> HEADER_MAPPER =
            JdbcOrderRepository::mapHeader;
    private static final RowMapper<OrderItemSnapshot> ITEM_MAPPER =
            (rs, rowNum) -> new OrderItemSnapshot(
                    rs.getLong("product_id"),
                    rs.getString("product_name"),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("unit_price_crc"));

    private final JdbcTemplate jdbc;

    public JdbcOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional
    public Order saveDraft(OrderDraft draft) {
        Objects.requireNonNull(draft, "draft");
        ShippingAddress address = draft.shippingAddress();
        OrderTotals totals = draft.totals();

        Long id = jdbc.queryForObject("""
                INSERT INTO orders (
                    user_id, status, payment_mode,
                    ship_province, ship_canton, ship_district, ship_details,
                    ship_recipient_name, ship_phone,
                    subtotal_crc, shipping_crc, tax_crc, total_crc
                ) VALUES (
                    ?, 'PENDING', 'SIMULATED',
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?
                ) RETURNING id
                """, Long.class,
                draft.userId(),
                address.province(), address.canton(), address.district(),
                address.details(), address.recipientName(), address.phone(),
                totals.subtotalCrc(), totals.shippingCrc(),
                totals.taxCrc(), totals.totalCrc());
        long orderId = Objects.requireNonNull(id, "id generado por PostgreSQL");

        int lineNumber = 1;
        for (OrderItemSnapshot item : draft.items()) {
            jdbc.update("""
                    INSERT INTO order_items (
                        order_id, line_number, product_id, product_name,
                        quantity, unit_price_crc, line_total_crc
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, orderId, lineNumber++, item.productId(), item.name(),
                    item.quantity(), item.unitPriceCrc(), item.lineTotalCrc());
        }
        return findByIdForUser(draft.userId(), orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo recuperar el pedido guardado"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdForUser(long userId, long orderId) {
        if (userId < 1 || orderId < 1) {
            return Optional.empty();
        }
        return readOrder("WHERE id = ? AND user_id = ?", orderId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdForAdmin(long orderId) {
        if (orderId < 1) {
            return Optional.empty();
        }
        return readOrder("WHERE id = ?", orderId);
    }

    private Optional<Order> readOrder(String whereClause, Object... params) {
        // whereClause proviene únicamente de literales de esta clase,
        // nunca de parámetros HTTP o del usuario.
        List<OrderHeader> headers = jdbc.query("""
                SELECT id, user_id, status, payment_mode,
                       ship_province, ship_canton, ship_district, ship_details,
                       ship_recipient_name, ship_phone,
                       subtotal_crc, shipping_crc, tax_crc, total_crc, created_at
                FROM orders
                """ + whereClause, HEADER_MAPPER, params);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        OrderHeader header = headers.getFirst();
        List<OrderItemSnapshot> items = jdbc.query("""
                SELECT product_id, product_name, quantity, unit_price_crc
                FROM order_items
                WHERE order_id = ?
                ORDER BY line_number
                """, ITEM_MAPPER, header.id());
        return Optional.of(new Order(
                header.id(), header.userId(), header.status(),
                header.paymentMode(), header.address(), items,
                header.totals(), header.createdAt()));
    }

    private static OrderHeader mapHeader(ResultSet rs, int row)
            throws SQLException {
        return new OrderHeader(
                rs.getLong("id"), rs.getLong("user_id"),
                OrderStatus.valueOf(rs.getString("status")),
                PaymentMode.valueOf(rs.getString("payment_mode")),
                new ShippingAddress(
                        rs.getString("ship_province"),
                        rs.getString("ship_canton"),
                        rs.getString("ship_district"),
                        rs.getString("ship_details"),
                        rs.getString("ship_recipient_name"),
                        rs.getString("ship_phone")),
                new OrderTotals(
                        rs.getBigDecimal("subtotal_crc"),
                        rs.getBigDecimal("shipping_crc"),
                        rs.getBigDecimal("tax_crc"),
                        rs.getBigDecimal("total_crc")),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private record OrderHeader(
            long id, long userId, OrderStatus status, PaymentMode paymentMode,
            ShippingAddress address, OrderTotals totals,
            OffsetDateTime createdAt) { }
}
