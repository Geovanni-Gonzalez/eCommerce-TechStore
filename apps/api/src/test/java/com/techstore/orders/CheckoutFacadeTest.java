package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.domain.CartLine;
import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.OrderDraft;
import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.domain.PaymentMode;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.events.OrderCreatedEvent;
import com.techstore.orders.events.OrderEventPublisher;
import com.techstore.orders.inventory.CheckoutStockRepository;
import com.techstore.orders.inventory.InsufficientStockException;
import com.techstore.orders.pricing.CheckoutTotalsCalculator;
import com.techstore.orders.pricing.EmptyCheckoutCartException;
import com.techstore.orders.pricing.UnavailableCheckoutItemException;
import com.techstore.orders.repository.OrderRepository;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.orders.shipping.FixedShippingStrategy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CheckoutFacadeTest {
    private final FakeCart cart = new FakeCart();
    private final FakeStock stock = new FakeStock();
    private final FakeOrders orders = new FakeOrders();
    private final FakeEvents events = new FakeEvents();
    private final CheckoutFacade facade = new CheckoutFacade(
            cart, stock,
            new CheckoutTotalsCalculator(
                    new FixedShippingStrategy(new BigDecimal("500.00")),
                    new BigDecimal("0.13")),
            orders, events);

    @Test
    void facadeCoordinatesValidationSnapshotReservationAndEvent() {
        Order order = facade.confirm(11, address());

        assertEquals(11, cart.ownerRead);
        assertEquals(11, stock.lockedOwner);
        assertEquals(List.of(7L), stock.lockedProducts);
        assertEquals(37L, order.id());
        assertEquals(OrderStatus.PENDING, order.status());
        assertEquals(PaymentMode.SIMULATED, order.paymentMode());
        assertEquals(new BigDecimal("1000.00"), order.totals().subtotalCrc());
        assertEquals(new BigDecimal("500.00"), order.totals().shippingCrc());
        assertEquals(new BigDecimal("130.00"), order.totals().taxCrc());
        assertEquals(new BigDecimal("1630.00"), order.totals().totalCrc());
        assertEquals("Mouse original", order.items().getFirst().name());
        assertEquals(1, stock.reservationCalls);
        assertEquals(37, stock.reservationOrderId);
        assertTrue(cart.cleared);
        assertEquals(37, events.last.orderId());
        assertEquals(new BigDecimal("1630.00"), events.last.totalCrc());
    }

    @Test
    void emptyCartDoesNotCreateOrderOrReserveStock() {
        cart.lines = List.of();
        assertThrows(EmptyCheckoutCartException.class,
                () -> facade.confirm(11, address()));
        assertEquals(0, stock.reservationCalls);
        assertEquals(0, orders.saved);
        assertNull(events.last);
    }

    @Test
    void unavailableItemStopsBeforeOrderAndStock() {
        cart.lines = List.of(new CartLine(7, "SKU", "Mouse original",
                new BigDecimal("500.00"), 2, 1, true));
        assertThrows(UnavailableCheckoutItemException.class,
                () -> facade.confirm(11, address()));
        assertEquals(0, orders.saved);
        assertEquals(0, stock.reservationCalls);
        assertFalse(cart.cleared);
    }

    @Test
    void stockRacePreventsClearingCartOrPublishingEvent() {
        stock.failReservation = true;
        assertThrows(InsufficientStockException.class,
                () -> facade.confirm(11, address()));
        assertFalse(cart.cleared);
        assertNull(events.last);
    }

    @Test
    void invalidPrincipalAndAddressAreRejectedBeforeLocking() {
        assertThrows(IllegalArgumentException.class,
                () -> facade.confirm(0, address()));
        assertThrows(IllegalArgumentException.class,
                () -> facade.confirm(11, null));
        assertEquals(0, stock.lockedOwner);
    }

    private static ShippingAddress address() {
        return new ShippingAddress("San José", "Central", "Carmen",
                "Frente a la plaza, casa azul", "Ana Pérez", "8888-1111");
    }

    private static final class FakeCart implements CartRepository {
        private long ownerRead;
        private boolean cleared;
        private List<CartLine> lines = List.of(new CartLine(7, "SKU", "Mouse original",
                new BigDecimal("500.00"), 2, 5, true));
        @Override public List<CartLine> findItems(long userId) {
            ownerRead = userId;
            return lines;
        }
        @Override public void clear(long userId) { cleared = true; }
        @Override public boolean containsProduct(long userId, long id) { return false; }
        @Override public void ensureCart(long userId) { }
        @Override public boolean incrementProduct(long userId, long id, int q) { return false; }
        @Override public boolean setQuantity(long userId, long id, int q) { return false; }
        @Override public boolean removeProduct(long userId, long id) { return false; }
    }

    private static final class FakeStock implements CheckoutStockRepository {
        private long lockedOwner;
        private List<Long> lockedProducts;
        private boolean failReservation;
        private int reservationCalls;
        private long reservationOrderId;
        @Override public void lockCustomerCart(long userId) { lockedOwner = userId; }
        @Override public void lockProducts(List<Long> ids) { lockedProducts = ids; }
        @Override public void reserveForOrder(long id, List<CartLine> lines) {
            reservationCalls++;
            if (failReservation) throw new InsufficientStockException();
            reservationOrderId = id;
        }
        @Override public void releaseReservations(long id) { }
        @Override public void consumeReservations(long id) { }
    }

    private static final class FakeOrders implements OrderRepository {
        private int saved;
        @Override public Order saveDraft(OrderDraft draft) {
            saved++;
            return new Order(37, draft.userId(), OrderStatus.PENDING,
                    PaymentMode.SIMULATED, draft.shippingAddress(),
                    draft.items(), draft.totals(), OffsetDateTime.now());
        }
        @Override public Optional<Order> findByIdForUser(long userId, long id) {
            return Optional.empty();
        }
    }

    private static final class FakeEvents implements OrderEventPublisher {
        private OrderCreatedEvent last;
        @Override public void orderCreated(OrderCreatedEvent event) {
            last = event;
        }
    }
}
