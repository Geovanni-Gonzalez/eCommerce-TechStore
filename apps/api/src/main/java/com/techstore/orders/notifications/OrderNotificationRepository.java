package com.techstore.orders.notifications;

import com.techstore.orders.events.OrderCreatedEvent;
import java.util.List;

/** Puerto de persistencia de notificaciones desacoplado del checkout. */
public interface OrderNotificationRepository {
    void storeOrderReceived(OrderCreatedEvent event);
    List<OrderNotification> listForOwner(long userId, long orderId);
}
