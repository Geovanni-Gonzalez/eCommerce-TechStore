package com.techstore.orders.service;

import com.techstore.cart.domain.CartLine;
import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.OrderDraft;
import com.techstore.orders.domain.OrderItemSnapshot;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.events.OrderCreatedEvent;
import com.techstore.orders.events.OrderEventPublisher;
import com.techstore.orders.inventory.CheckoutStockRepository;
import com.techstore.orders.pricing.CheckoutTotalsCalculator;
import com.techstore.orders.repository.OrderRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GoF Facade: un solo caso de uso coordina carrito, bloqueo, precios, reserva,
 * snapshot del pedido, vaciado del carrito y señal de evento.
 *
 * @Transactional garantiza atomicidad y rollback integral. El Observer
 * concreto de BKL-068 reacciona AFTER_COMMIT (best effort, sin outbox).
 */
@Service
public class CheckoutFacade {
    private final CartRepository carts;
    private final CheckoutStockRepository stock;
    private final CheckoutTotalsCalculator pricing;
    private final OrderRepository orders;
    private final OrderEventPublisher events;

    public CheckoutFacade(CartRepository carts, CheckoutStockRepository stock,
            CheckoutTotalsCalculator pricing, OrderRepository orders,
            OrderEventPublisher events) {
        this.carts = Objects.requireNonNull(carts);
        this.stock = Objects.requireNonNull(stock);
        this.pricing = Objects.requireNonNull(pricing);
        this.orders = Objects.requireNonNull(orders);
        this.events = Objects.requireNonNull(events);
    }

    @Transactional
    public Order confirm(long userId, ShippingAddress address) {
        if (userId < 1 || address == null) {
            throw new IllegalArgumentException("Usuario o dirección inválida");
        }
        // 1. Bloquea el carrito y sus líneas para evitar confirmaciones dobles.
        stock.lockCustomerCart(userId);
        List<CartLine> initial = carts.findItems(userId);
        if (initial.isEmpty()) {
            throw new com.techstore.orders.pricing.EmptyCheckoutCartException();
        }

        // 2. Bloquea productos en orden estable; luego lee nuevamente el
        // catálogo BAJO BLOQUEO para fijar el precio y validar stock vigentes.
        stock.lockProducts(initial.stream().map(CartLine::productId).toList());
        List<CartLine> current = carts.findItems(userId);
        var totals = pricing.calculate(current);
        List<OrderItemSnapshot> lines = current.stream()
                .map(line -> new OrderItemSnapshot(
                        line.productId(), line.name(), line.quantity(),
                        line.unitPriceCrc()))
                .toList();

        // 3. Pedido, reserva, carrito y señal de evento bajo una transacción.
        // El Observer audita solo después del commit efectivo.
        OrderDraft draft = new OrderDraft(userId, address, lines, totals);
        Order order = orders.saveDraft(draft);
        stock.reserveForOrder(order.id(), current);
        carts.clear(userId);
        events.orderCreated(new OrderCreatedEvent(
                order.id(), userId, order.totals().totalCrc()));
        return order;
    }
}
