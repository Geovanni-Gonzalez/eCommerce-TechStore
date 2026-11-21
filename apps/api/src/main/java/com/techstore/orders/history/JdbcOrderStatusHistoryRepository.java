package com.techstore.orders.history;

import com.techstore.orders.domain.OrderStatus;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Auditoría JDBC: participa en la transacción existente. No usar REQUIRES_NEW,
 * pues una cancelación revertida no debe dejar historial falso.
 */
@Repository
public class JdbcOrderStatusHistoryRepository implements OrderStatusHistoryRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrderStatusHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void recordCreated(long orderId, long ownerId) {
        recordTransition(orderId, null, OrderStatus.PENDING,
                OrderChangeActor.CLIENT, ownerId, OrderChangeReason.CREATED);
    }

    @Override
    public void recordTransition(long orderId, OrderStatus from, OrderStatus to,
            OrderChangeActor actorType, Long actorId, OrderChangeReason reason) {
        if (orderId < 1 || to == null || actorType == null || reason == null
                || from == to || (actorType == OrderChangeActor.SYSTEM) != (actorId == null)
                || (actorId != null && actorId < 1)) {
            throw new IllegalArgumentException("Registro de cambio de estado inválido");
        }
        jdbc.update("""
                INSERT INTO order_status_history (
                    order_id, from_status, to_status, actor_type, actor_id, reason
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, orderId, from == null ? null : from.name(), to.name(),
                actorType.name(), actorId, reason.name());
    }

    @Override
    public List<OrderStatusHistoryEntry> findByOrderId(long orderId) {
        if (orderId < 1) {
            throw new IllegalArgumentException("Pedido inválido");
        }
        return jdbc.query("""
                SELECT id, order_id, from_status, to_status,
                    actor_type, actor_id, reason, changed_at
                FROM order_status_history WHERE order_id = ? ORDER BY id
                """, (rs, row) -> new OrderStatusHistoryEntry(
                    rs.getLong("id"),
                    rs.getLong("order_id"),
                    rs.getString("from_status") == null ? null
                        : OrderStatus.valueOf(rs.getString("from_status")),
                    OrderStatus.valueOf(rs.getString("to_status")),
                    OrderChangeActor.valueOf(rs.getString("actor_type")),
                    rs.getObject("actor_id", Long.class),
                    OrderChangeReason.valueOf(rs.getString("reason")),
                    rs.getObject("changed_at", java.time.OffsetDateTime.class)),
                orderId);
    }
}
