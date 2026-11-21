package com.techstore.orders.repository;

import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.OrderDraft;
import java.util.Optional;

/**
 * Puerto de persistencia para CheckoutFacade futuro (BKL-033/BKL-067).
 * Por ahora NO existe endpoint HTTP que permita persistir un pedido.
 */
public interface OrderRepository {
    Order saveDraft(OrderDraft draft);
    Optional<Order> findByIdForUser(long userId, long orderId);
    /** Solo invocar desde rutas con ROLE_ADMIN. */
    Optional<Order> findByIdForAdmin(long orderId);
}
