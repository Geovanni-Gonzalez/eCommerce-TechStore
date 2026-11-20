package com.techstore.orders.events;

import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adaptador a eventos Spring. El Observer de BKL-068 registra auditoría
 * AFTER_COMMIT mediante un listener desacoplado. No garantiza entrega durable.
 */
@Component
public final class SpringOrderEventPublisher implements OrderEventPublisher {
    private final ApplicationEventPublisher delegate;

    public SpringOrderEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void orderCreated(OrderCreatedEvent event) {
        delegate.publishEvent(Objects.requireNonNull(event));
    }
}
