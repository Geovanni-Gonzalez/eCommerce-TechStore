package com.techstore.orders.inventory;

import com.techstore.orders.domain.OrderStatus;
import java.util.List;
import java.util.Optional;

/** Puerto para PENDING -> CANCELLED con serialización por cabecera. */
public interface OrderReservationLifecycleRepository {
    Optional<OrderStatus> lockOwnedOrder(long userId, long orderId);
    Optional<OrderStatus> lockOrder(long orderId);
    List<Long> findExpiredOrders(int limit);
    boolean hasExpiredReservations(long orderId);
    void markCancelled(long orderId);
}
