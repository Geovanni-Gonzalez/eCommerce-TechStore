package com.techstore.orders.notifications;

import com.techstore.orders.events.OrderCreatedEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** La escritura posterior al commit del pedido requiere transacción nueva. */
@Repository
public class JdbcOrderNotificationRepository implements OrderNotificationRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrderNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeOrderReceived(OrderCreatedEvent event) {
        Objects.requireNonNull(event);
        if (event.orderId() < 1 || event.userId() < 1) {
            throw new IllegalArgumentException("Evento de pedido inválido");
        }
        jdbc.update("""
                INSERT INTO order_notifications (order_id,user_id,event_type,message)
                SELECT id,user_id,'ORDER_RECEIVED',
                       'Recibimos tu pedido. Puedes consultar su estado en Mis pedidos.'
                FROM orders WHERE id=? AND user_id=?
                ON CONFLICT (order_id,event_type) DO NOTHING
                """, event.orderId(), event.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderNotification> listForOwner(long userId, long orderId) {
        if (userId < 1 || orderId < 1) return List.of();
        return jdbc.query("""
                SELECT n.id,n.order_id,n.event_type,n.message,n.created_at
                FROM order_notifications n
                JOIN orders o ON o.id=n.order_id AND o.user_id=n.user_id
                WHERE n.user_id=? AND n.order_id=? AND o.user_id=?
                ORDER BY n.id
                """, (rs, row) -> new OrderNotification(
                    rs.getLong("id"), rs.getLong("order_id"),
                    rs.getString("event_type"), rs.getString("message"),
                    rs.getObject("created_at", OffsetDateTime.class)),
                userId, orderId, userId);
    }
}
