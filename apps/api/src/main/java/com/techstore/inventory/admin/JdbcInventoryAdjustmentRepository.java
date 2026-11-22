package com.techstore.inventory.admin;

import com.techstore.inventory.domain.InventoryMovement;
import com.techstore.inventory.domain.InventoryMovementKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de escritura exclusivo ADMIN. La transacción la define el servicio.
 * El bloqueo de products serializa ajustes contra reservas del checkout.
 */
@Repository
public class JdbcInventoryAdjustmentRepository {
    private static final String AUDIT_SELECT = """
            SELECT id,product_id,order_id,kind,quantity,
                   available_delta,reserved_delta,physical_delta,
                   recorded_at,actor_user_id,reason,request_id
            FROM inventory_movements
            """;
    private final JdbcTemplate jdbc;

    public JdbcInventoryAdjustmentRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public Optional<Integer> lockProductStock(long productId) {
        return jdbc.query("""
                SELECT stock FROM products WHERE id=? FOR UPDATE
                """, (rs,row)->rs.getInt("stock"), productId).stream().findFirst();
    }

    public int addAvailable(long productId, int quantity) {
        return jdbc.update("""
                UPDATE products SET stock=stock+?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND stock <= 2147483647 - ?
                """, quantity,productId,quantity);
    }

    public int removeAvailable(long productId, int quantity) {
        return jdbc.update("""
                UPDATE products SET stock=stock-?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND stock>=?
                """, quantity,productId,quantity);
    }

    public long recordAdjustment(
            long adminId,long productId,InventoryAdjustmentCommand command) {
        long availableDelta = command.kind()==InventoryMovementKind.ADJUSTMENT_IN
                ? command.quantity() : -((long)command.quantity());
        Long id = jdbc.queryForObject("""
                INSERT INTO inventory_movements (
                    product_id,order_id,kind,quantity,available_delta,
                    reserved_delta,physical_delta,actor_user_id,reason,request_id
                ) VALUES (?,NULL,?,?,?,0,?,?,?,?) RETURNING id
                """, Long.class,
                productId,command.kind().name(),command.quantity(),availableDelta,
                availableDelta,adminId,command.reason(),command.requestId());
        if (id == null) throw new IllegalStateException("Movimiento sin ID");
        return id;
    }

    public Optional<InventoryMovementAudit> findByRequestId(UUID requestId) {
        return jdbc.query(AUDIT_SELECT + " WHERE request_id=?",
                JdbcInventoryAdjustmentRepository::toAudit,requestId)
                .stream().findFirst();
    }

    public List<InventoryMovementAudit> listMovements(
            long productId,int limit,long offset) {
        return jdbc.query(AUDIT_SELECT + """
                WHERE product_id=?
                ORDER BY recorded_at DESC,id DESC LIMIT ? OFFSET ?
                """, JdbcInventoryAdjustmentRepository::toAudit,
                productId,limit,offset);
    }

    public long countMovements(long productId) {
        Long n=jdbc.queryForObject("""
                SELECT COUNT(*) FROM inventory_movements WHERE product_id=?
                """,Long.class,productId);
        return n==null?0:n;
    }

    public long countProducts() {
        Long n=jdbc.queryForObject("SELECT COUNT(*) FROM products",Long.class);
        return n==null?0:n;
    }

    private static InventoryMovementAudit toAudit(
            ResultSet rs,int rowNum) throws SQLException {
        long orderValue=rs.getLong("order_id");
        Long orderId=rs.wasNull()?null:orderValue;
        long actorValue=rs.getLong("actor_user_id");
        Long actorId=rs.wasNull()?null:actorValue;
        UUID requestId=rs.getObject("request_id",UUID.class);
        InventoryMovement movement = new InventoryMovement(
                rs.getLong("id"),rs.getLong("product_id"),orderId,
                InventoryMovementKind.valueOf(rs.getString("kind")),
                rs.getLong("quantity"),rs.getLong("available_delta"),
                rs.getLong("reserved_delta"),rs.getLong("physical_delta"),
                rs.getObject("recorded_at",OffsetDateTime.class));
        return new InventoryMovementAudit(
                movement,actorId,rs.getString("reason"),requestId);
    }
}
