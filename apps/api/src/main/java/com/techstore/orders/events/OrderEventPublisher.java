package com.techstore.orders.events;

/** Punto de extensión para Observer (BKL-068), sin acoplar la fachada a listeners. */
public interface OrderEventPublisher {
    void orderCreated(OrderCreatedEvent event);
}
