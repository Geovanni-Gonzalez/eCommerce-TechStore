package com.techstore.orders.events;

import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adaptador a eventos Spring. Por ahora NO hay Observer concreto ni outbox:
 * BKL-068 agregará un listener AFTER_COMMIT con evidencia auditable.
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
