package com.techstore.orders.service;

import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.inventory.CheckoutStockRepository;
import com.techstore.orders.inventory.OrderReservationLifecycleRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Cancelación y vencimiento atómicos con bloqueo de cabecera primero. */
@Service
public class OrderReservationService {
    private final OrderReservationLifecycleRepository orders;
    private final CheckoutStockRepository stock;

    public OrderReservationService(OrderReservationLifecycleRepository orders,
            CheckoutStockRepository stock) {
        this.orders = Objects.requireNonNull(orders);
        this.stock = Objects.requireNonNull(stock);
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
        return true;
    }
}
