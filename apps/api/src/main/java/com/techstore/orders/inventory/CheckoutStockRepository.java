package com.techstore.orders.inventory;

import com.techstore.cart.domain.CartLine;
import java.util.List;

/**
 * Abstracción para proteger concurrencia. Descontar stock y registrar reservas
 * exige la misma transacción que la creación del pedido.
 */
public interface CheckoutStockRepository {
    void lockCustomerCart(long userId);
    void lockProducts(List<Long> productIds);
    void reserveForOrder(long orderId, List<CartLine> lines);

    /** Para flujo futuro de cancelación: solo RESERVED -> RELEASED. */
    void releaseReservations(long orderId);

    /** Para flujo futuro de confirmación: solo RESERVED -> CONSUMED. */
    void consumeReservations(long orderId);
}
