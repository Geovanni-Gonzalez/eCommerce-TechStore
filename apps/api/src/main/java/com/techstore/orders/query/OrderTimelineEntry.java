package com.techstore.orders.query;

import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.history.OrderChangeActor;
import com.techstore.orders.history.OrderChangeReason;
import com.techstore.orders.history.OrderStatusHistoryEntry;
import java.time.OffsetDateTime;

/**
 * Historial visible a CLIENT: sin actorId ni IDs de auditoría internos.
 * Los detalles completos permanecen solo para ADMIN (BKL-045).
 */
public record OrderTimelineEntry(
        OrderStatus fromStatus, OrderStatus toStatus,
        OrderChangeActor actorType, OrderChangeReason reason,
        OffsetDateTime changedAt
) {
    public static OrderTimelineEntry from(OrderStatusHistoryEntry entry) {
        return new OrderTimelineEntry(entry.fromStatus(), entry.toStatus(),
                entry.actorType(), entry.reason(), entry.changedAt());
    }
}
