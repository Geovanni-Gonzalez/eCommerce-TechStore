package com.techstore.orders.service;

import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.payments.PaymentExposurePolicy;
import com.techstore.orders.payments.PaymentIncidentReason;
import com.techstore.orders.payments.PaymentIncidentRepository;
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
    private final PaymentExposurePolicy exposure;
    private final PaymentIncidentRepository incidents;

    public OrderReservationService(OrderReservationLifecycleRepository orders,
            CheckoutStockRepository stock, OrderStatusHistoryRepository history,
            PaymentExposurePolicy exposure, PaymentIncidentRepository incidents) {
        this.orders = Objects.requireNonNull(orders);
        this.stock = Objects.requireNonNull(stock);
        this.history = Objects.requireNonNull(history);
        this.exposure = Objects.requireNonNull(exposure);
        this.incidents = Objects.requireNonNull(incidents);
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
        ensureSafeToRelease(orderId);
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
        PaymentIncidentReason reason = exposure.blockingReason(orderId);
        if (reason != null) {
            // Una decisión de retención debe COMMIT para que ADMIN pueda verla.
            incidents.record(orderId, reason, null);
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
        ensureSafeToRelease(orderId);
        stock.releaseReservations(orderId);
        orders.markCancelled(orderId);
        history.recordTransition(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED,
                OrderChangeActor.ADMIN, actorId, OrderChangeReason.ADMIN_CANCEL);
    }

    private void ensureSafeToRelease(long orderId) {
        if (exposure.blockingReason(orderId) != null) {
            // No se presume que hubo rechazo ni que es seguro liberar stock.
            throw new OrderNotCancellableException();
        }
    }

    @Transactional
    public List<OrderStatusHistoryEntry> historyForAdmin(long orderId) {
        if (orderId < 1 || orders.lockOrder(orderId).isEmpty()) {
            throw new OrderNotFoundException();
        }
        return history.findByOrderId(orderId);
    }
}
