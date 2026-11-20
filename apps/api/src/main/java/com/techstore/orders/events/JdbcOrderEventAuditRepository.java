package com.techstore.orders.events;

import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La reacción AFTER_COMMIT debe usar una transacción NUEVA: la transacción
 * del checkout ya terminó. La restricción única evita auditorías duplicadas.
 */
@Repository
public class JdbcOrderEventAuditRepository implements OrderEventAuditRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrderEventAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCreated(OrderCreatedEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.orderId() < 1 || event.totalCrc() == null
                || event.totalCrc().signum() < 0) {
            throw new IllegalArgumentException("Evento de pedido inválido");
        }
        jdbc.update("""
                INSERT INTO order_event_audit (order_id, event_type, total_crc)
                VALUES (?, 'ORDER_CREATED', ?)
                ON CONFLICT (order_id, event_type) DO NOTHING
                """, event.orderId(), event.totalCrc());
    }
}
