package com.techstore.orders.query;

import com.techstore.orders.domain.Order;
import com.techstore.orders.dto.OrderResponse;
import com.techstore.orders.history.OrderStatusHistoryRepository;
import com.techstore.orders.repository.OrderRepository;
import com.techstore.orders.service.OrderNotFoundException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BKL-042: control de acceso en API + predicado user_id dentro de SQL.
 * El cliente nunca indica ownerId ni obtiene actorId. ADMIN tiene API propia.
 */
@Service
public class OrderQueryService {
    private final OrderRepository orders;
    private final OrderListRepository listing;
    private final OrderStatusHistoryRepository history;

    public OrderQueryService(OrderRepository orders, OrderListRepository listing,
            OrderStatusHistoryRepository history) {
        this.orders = Objects.requireNonNull(orders);
        this.listing = Objects.requireNonNull(listing);
        this.history = Objects.requireNonNull(history);
    }

    @Transactional(readOnly = true)
    public OrderPage<OrderSummary> listMine(long userId, int limit, Long beforeId) {
        validatePage(limit, beforeId);
        return listing.listForOwner(userId, limit, beforeId);
    }

    @Transactional(readOnly = true)
    public OrderResponse detailMine(long userId, long orderId) {
        return OrderResponse.from(orders.findByIdForUser(userId, orderId)
                .orElseThrow(OrderNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<OrderTimelineEntry> historyMine(long userId, long orderId) {
        // Siempre comprobar propietario antes de leer historial completo.
        orders.findByIdForUser(userId, orderId)
                .orElseThrow(OrderNotFoundException::new);
        return history.findByOrderId(orderId).stream()
                .map(OrderTimelineEntry::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderPage<AdminOrderSummary> listAdmin(int limit, Long beforeId) {
        validatePage(limit, beforeId);
        return listing.listForAdmin(limit, beforeId);
    }

    @Transactional(readOnly = true)
    public AdminOrderDetail detailAdmin(long orderId) {
        Order order = orders.findByIdForAdmin(orderId)
                .orElseThrow(OrderNotFoundException::new);
        return new AdminOrderDetail(order.userId(), OrderResponse.from(order));
    }

    private static void validatePage(int limit, Long beforeId) {
        if (limit < 1 || limit > 100 || (beforeId != null && beforeId < 1)) {
            throw new InvalidOrderPageRequestException();
        }
    }
}
