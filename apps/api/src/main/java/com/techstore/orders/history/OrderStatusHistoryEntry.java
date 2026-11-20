package com.techstore.orders.history;

import com.techstore.orders.domain.OrderStatus;
import java.time.OffsetDateTime;

/** Proyección sin PII, visible solo al administrador autenticado. */
public record OrderStatusHistoryEntry(
        long id, long orderId, OrderStatus fromStatus, OrderStatus toStatus,
        OrderChangeActor actorType, Long actorId, OrderChangeReason reason,
        OffsetDateTime changedAt
) { }
