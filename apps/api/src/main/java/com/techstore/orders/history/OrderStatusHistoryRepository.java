package com.techstore.orders.history;

import com.techstore.orders.domain.OrderStatus;
import java.util.List;

/** Puerto de auditoría en la MISMA transacción del pedido y stock. */
public interface OrderStatusHistoryRepository {
    void recordCreated(long orderId, long ownerId);
    void recordTransition(long orderId, OrderStatus from, OrderStatus to,
            OrderChangeActor actorType, Long actorId, OrderChangeReason reason);
    List<OrderStatusHistoryEntry> findByOrderId(long orderId);
}
