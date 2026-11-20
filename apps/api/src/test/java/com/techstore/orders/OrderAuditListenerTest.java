package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.orders.events.OrderAuditListener;
import com.techstore.orders.events.OrderCreatedEvent;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OrderAuditListenerTest {
    private static final OrderCreatedEvent EVENT =
            new OrderCreatedEvent(7, 11, new BigDecimal("1499.50"));

    @Test
    void concreteObserverDelegatesToAuditPort() {
        var calls = new AtomicInteger();
        var listener = new OrderAuditListener(event -> {
            assertEquals(EVENT, event);
            calls.incrementAndGet();
        });
        listener.onOrderCreated(EVENT);
        assertEquals(1, calls.get());
    }

    @Test
    void postCommitAuditErrorDoesNotTurnCommittedOrderIntoHttpFailure() {
        var listener = new OrderAuditListener(event -> {
            throw new IllegalStateException("Base de auditoría no disponible");
        });
        assertDoesNotThrow(() -> listener.onOrderCreated(EVENT));
    }
}
