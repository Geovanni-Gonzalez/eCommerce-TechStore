package com.techstore.orders.notifications;

import java.time.OffsetDateTime;

/** Mensaje visible al cliente; nunca incorpora email, teléfono ni dirección. */
public record OrderNotification(long id, long orderId, String type,
                                String message, OffsetDateTime createdAt) { }
