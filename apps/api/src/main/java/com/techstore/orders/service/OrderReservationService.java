package com.techstore.orders.service;

import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.inventory.CheckoutStockRepository;
import com.techstore.orders.inventory.OrderReservationLifecycleRepository;
import com.techstore.orders.history.OrderChangeActor;
import com.techstore.orders.history.OrderChangeReason;
import com.techstore.orders.history.OrderStatusHistoryEntry;
import com.techstore.orders.history.OrderStatusHistoryRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Cancelación y vencimiento atómicos con bloqueo de cabecera primero. */
@Service
public class OrderReservationService {
    private final OrderReservationLifecycleRepository orders;
    private final CheckoutStockRepository stock;
    private final OrderStatusHistoryRepository history;

    public OrderReservationService(OrderReservationLifecycleRepository orders,
            CheckoutStockRepository stock, OrderStatusHistoryRepository history) {
        this.orders = Objects.requireNonNull(orders);
        this.stock = Objects.requireNonNull(stock);
        this.history = Objects.requireNonNull(history);
    }

    @Transactional
    public void cancelOwned(long userId, long orderId) {
        if (userId < 1 || orderId < 1) {
            throw new OrderNotFoundException();
        }
        OrderStatus status = orders.lockOwnedOrder(userId, orderId)
                .orElseThrow(OrderNotFoundException::new);
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        if (status != OrderStatus.PENDING) {
            throw new OrderNotCancellableException();
        }
        stock.releaseReservations(orderId);
        orders.markCancelled(orderId);
        history.recordTransition(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED,
                OrderChangeActor.CLIENT, userId, OrderChangeReason.CLIENT_CANCEL);
    }

    /** Llamar desde otra instancia Spring para ejecutar REQUIRES_NEW. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireOne(long orderId) {
        if (orderId < 1) {
            return false;
        }
        OrderStatus status = orders.lockOrder(orderId).orElse(null);
        if (status != OrderStatus.PENDING || !orders.hasExpiredReservations(orderId)) {
            return false;
        }
        stock.releaseReservations(orderId);
        orders.markCancelled(orderId);
        history.recordTransition(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED,
                OrderChangeActor.SYSTEM, null, OrderChangeReason.EXPIRED);
        return true;
    }

    /**
     * Única transición administrativa habilitada: PENDING -> CANCELLED.
     * BKL-040 habilita CONFIRMED solo con prueba de sandbox verificada y opt-in.
     */
    @Transactional
    public void cancelAsAdmin(long actorId, long orderId) {
        if (actorId < 1 || orderId < 1) {
            throw new OrderNotFoundException();
        }
        OrderStatus current = orders.lockOrder(orderId)
                .orElseThrow(OrderNotFoundException::new);
        if (current != OrderStatus.PENDING) {
            throw new OrderNotCancellableException();
        }
        stock.releaseReservations(orderId);
        orders.markCancelled(orderId);
        history.recordTransition(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED,
                OrderChangeActor.ADMIN, actorId, OrderChangeReason.ADMIN_CANCEL);
    }

    @Transactional
    public List<OrderStatusHistoryEntry> historyForAdmin(long orderId) {
        if (orderId < 1 || orders.lockOrder(orderId).isEmpty()) {
            throw new OrderNotFoundException();
        }
        return history.findByOrderId(orderId);
    }
}
