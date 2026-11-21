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
        // Priorizar pedidos sin intento potencialmente ejecutado evita que
        // cientos de incidencias retenidas bloqueen la liberación de otros.
        return jdbc.query("""
                SELECT q.order_id FROM (
                    SELECT DISTINCT r.order_id FROM stock_reservations r
                    WHERE r.status='RESERVED' AND r.expires_at<=CURRENT_TIMESTAMP
                ) q
                ORDER BY EXISTS (
                    SELECT 1 FROM payment_attempts a
                    WHERE a.order_id=q.order_id
                    AND a.status IN ('STARTED','UNKNOWN','SIMULATED_APPROVED')),
                    q.order_id
                LIMIT ?
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
