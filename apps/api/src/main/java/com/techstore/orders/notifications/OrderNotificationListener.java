package com.techstore.orders.notifications;

import com.techstore.orders.events.OrderCreatedEvent;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Segundo Observer del checkout, separado de auditoría. Una falla del canal
 * externo o del repositorio posterior al commit nunca revierte el pedido.
 */
@Component
public class OrderNotificationListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderNotificationListener.class);
    private final OrderNotificationRepository notifications;
    private final OrderNotificationChannel channel;

    public OrderNotificationListener(OrderNotificationRepository notifications,
            OrderNotificationChannel channel) {
        this.notifications = Objects.requireNonNull(notifications);
        this.channel = Objects.requireNonNull(channel);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            notifications.storeOrderReceived(event);
        } catch (RuntimeException failure) {
            LOG.error("No se pudo registrar notificación para pedido {}", event.orderId(), failure);
        }
        try {
            channel.notifyOrderReceived(event);
        } catch (RuntimeException failure) {
            LOG.warn("Canal externo de notificación falló para pedido {}", event.orderId(), failure);
        }
    }
}
