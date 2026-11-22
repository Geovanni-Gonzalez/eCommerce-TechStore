package com.techstore.orders.notifications;

import com.techstore.orders.events.OrderCreatedEvent;
import org.springframework.stereotype.Component;

/** Sin SMTP configurado. No afirma haber enviado un correo real. */
@Component
public class DisabledEmailNotificationChannel implements OrderNotificationChannel {
    @Override
    public void notifyOrderReceived(OrderCreatedEvent event) {
        // La notificación in-app queda disponible sin proveedor externo.
    }
}
