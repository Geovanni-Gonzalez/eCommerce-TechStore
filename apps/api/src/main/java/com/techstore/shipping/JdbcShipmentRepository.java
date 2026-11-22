package com.techstore.shipping;

import com.techstore.orders.domain.OrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Objects;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Datos mínimos: sin duplicar PII del snapshot de orders ni simular guías.
 * Al registrar, ON CONFLICT(order_id) garantiza idempotencia.
 */
@Repository
public class JdbcShipmentRepository {
    private static final String PROJECTION = """
            SELECT id,order_id,fulfillment_method,status,
                   opened_by_user_id,registered_at
            FROM shipments
            """;
    private final JdbcTemplate jdbc;

    public JdbcShipmentRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** La cabecera de pedido se bloquea antes de registrar; evita carrera con pagos. */
    public Optional<OrderStatus> lockOrderStatus(long orderId) {
        return jdbc.query("""
                SELECT status FROM orders WHERE id=? FOR UPDATE
                """, (rs,row)->OrderStatus.valueOf(rs.getString("status")), orderId)
                .stream().findFirst();
    }

    public Optional<ShipmentRecord> findByOrderId(long orderId) {
        return jdbc.query(PROJECTION + " WHERE order_id=?",
                JdbcShipmentRepository::toRecord, orderId)
                .stream().findFirst();
    }

    /** Solo puede conocer pedidos propios un cliente con ROLE_CLIENT. */
    public Optional<OrderStatus> findOwnedOrderStatus(long userId,long orderId) {
        return jdbc.query("""
                SELECT status FROM orders WHERE id=? AND user_id=?
                """,(rs,row)->OrderStatus.valueOf(rs.getString("status")),
                orderId,userId).stream().findFirst();
    }

    public List<ShipmentHistoryEntry> findHistoryByOrderId(long orderId) {
        return jdbc.query("""
                SELECT h.id,h.shipment_id,h.from_status,h.to_status,
                       h.actor_user_id,h.reason,h.changed_at
                FROM shipment_status_history h
                JOIN shipments s ON s.id=h.shipment_id
                WHERE s.order_id=?
                ORDER BY h.changed_at ASC,h.id ASC
                """, (rs,row)->new ShipmentHistoryEntry(
                rs.getLong("id"),rs.getLong("shipment_id"),
                rs.getString("from_status")==null?null:
                    ShippingStatus.valueOf(rs.getString("from_status")),
                ShippingStatus.valueOf(rs.getString("to_status")),
                rs.getLong("actor_user_id"),rs.getString("reason"),
                rs.getObject("changed_at",OffsetDateTime.class)),orderId);
    }

    /** Bloqueo de orders adquirido por ShippingAdminService antes de este UPDATE. */
    public ShipmentRecord advanceManual(ShippingTransitionCommand command) {
        int changed=jdbc.update("""
                UPDATE shipments SET status=?,last_changed_by_user_id=?
                WHERE order_id=? AND status=?
                """,command.toStatus().name(),command.actorUserId(),
                command.orderId(),command.fromStatus().name());
        if(changed!=1)throw new ShippingInvalidTransitionException();
        return findByOrderId(command.orderId())
                .orElseThrow(ShippingNotFoundException::new);
    }

    public ShippingRegistration registerManual(ShippingRegistrationCommand command) {
        boolean created = !jdbc.query("""
                INSERT INTO shipments (order_id,opened_by_user_id)
                VALUES (?,?)
                ON CONFLICT (order_id) DO NOTHING
                RETURNING id
                """, (rs,row)->rs.getLong("id"),
                command.orderId(),command.actorUserId()).isEmpty();
        ShipmentRecord record = findByOrderId(command.orderId())
                .orElseThrow(IllegalStateException::new);
        return new ShippingRegistration(record,created);
    }

    private static ShipmentRecord toRecord(ResultSet rs,int row) throws SQLException {
        return new ShipmentRecord(rs.getLong("id"),rs.getLong("order_id"),
                rs.getString("fulfillment_method"),
                ShippingStatus.valueOf(rs.getString("status")),
                rs.getLong("opened_by_user_id"),
                rs.getObject("registered_at",OffsetDateTime.class));
    }
}
