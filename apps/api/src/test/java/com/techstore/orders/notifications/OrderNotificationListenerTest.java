package com.techstore.orders.notifications;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;
import com.techstore.orders.events.OrderCreatedEvent;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderNotificationListenerTest {
    private final OrderCreatedEvent event =
            new OrderCreatedEvent(17, 9, new BigDecimal("2500.00"));

    @Test
    void storeAndSendAreIndependent() {
        var repo = mock(OrderNotificationRepository.class);
        var channel = mock(OrderNotificationChannel.class);
        new OrderNotificationListener(repo, channel).onOrderCreated(event);
        verify(repo).storeOrderReceived(event);
        verify(channel).notifyOrderReceived(event);
    }

    @Test
    void externalEmailFailureDoesNotAbortOrderOrInAppNotification() {
        var repo = mock(OrderNotificationRepository.class);
        var channel = mock(OrderNotificationChannel.class);
        doThrow(new IllegalStateException("SMTP desconectado"))
                .when(channel).notifyOrderReceived(event);
        assertDoesNotThrow(() -> new OrderNotificationListener(repo, channel)
                .onOrderCreated(event));
        verify(repo).storeOrderReceived(event);
    }

    @Test
    void storageFailureDoesNotAbortOrder() {
        var repo = mock(OrderNotificationRepository.class);
        var channel = mock(OrderNotificationChannel.class);
        doThrow(new IllegalStateException("BD temporalmente caída"))
                .when(repo).storeOrderReceived(event);
        assertDoesNotThrow(() -> new OrderNotificationListener(repo, channel)
                .onOrderCreated(event));
        verify(channel).notifyOrderReceived(event);
    }
}
