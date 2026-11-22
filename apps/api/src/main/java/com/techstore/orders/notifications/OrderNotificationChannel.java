package com.techstore.orders.notifications;

import com.techstore.orders.events.OrderCreatedEvent;

/** Canal externo opcional; no participa en la transacción de compra. */
public interface OrderNotificationChannel {
    void notifyOrderReceived(OrderCreatedEvent event);
}
