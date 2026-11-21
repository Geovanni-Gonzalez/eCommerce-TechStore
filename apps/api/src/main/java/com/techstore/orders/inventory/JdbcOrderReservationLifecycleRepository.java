package com.techstore.orders.inventory;

import com.techstore.orders.domain.OrderStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bloquear orders primero; luego reservas/productos. BKL-040 aplica el mismo orden en reconciliación transaccional. */
@Repository
public class JdbcOrderReservationLifecycleRepository
        implements OrderReservationLifecycleRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrderReservationLifecycleRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<OrderStatus> lockOwnedOrder(long userId, long orderId) {
        List<String> statuses = jdbc.query("""
                SELECT status FROM orders WHERE id = ? AND user_id = ? FOR UPDATE
                """, (rs, row) -> rs.getString(1), orderId, userId);
        return statuses.stream().findFirst().map(OrderStatus::valueOf);
    }

    @Override
    public Optional<OrderStatus> lockOrder(long orderId) {
        List<String> statuses = jdbc.query("""
                SELECT status FROM orders WHERE id = ? FOR UPDATE
                """, (rs, row) -> rs.getString(1), orderId);
        return statuses.stream().findFirst().map(OrderStatus::valueOf);
    }

    @Override
    public List<Long> findExpiredOrders(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Límite de expiración inválido");
        }
        return jdbc.query("""
                SELECT DISTINCT order_id FROM stock_reservations
                WHERE status = 'RESERVED' AND expires_at <= CURRENT_TIMESTAMP
                ORDER BY order_id LIMIT ?
                """, (rs, row) -> rs.getLong(1), limit);
    }

    @Override
    public boolean hasExpiredReservations(long orderId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM stock_reservations
                    WHERE order_id = ? AND status = 'RESERVED'
                    AND expires_at <= CURRENT_TIMESTAMP)
                """, Boolean.class, orderId));
    }

    @Override
    public void markCancelled(long orderId) {
        int changed = jdbc.update("""
                UPDATE orders SET status='CANCELLED', updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='PENDING'
                """, orderId);
        if (changed != 1) {
            throw new IllegalStateException("Cambio concurrente del estado del pedido");
        }
    }
}
