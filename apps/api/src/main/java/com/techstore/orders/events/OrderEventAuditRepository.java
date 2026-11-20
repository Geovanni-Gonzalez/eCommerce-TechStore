package com.techstore.orders.events;

/** Puerto para evidencia persistente del observador, sin dependencias de JDBC. */
public interface OrderEventAuditRepository {
    void recordCreated(OrderCreatedEvent event);
}
