package com.techstore.orders.service;

import com.techstore.cart.domain.CartLine;
import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.dto.CheckoutQuoteResponse;
import com.techstore.orders.pricing.CheckoutTotalsCalculator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Obtiene el carrito por principal autenticado. Nunca acepta totales, userId
 * ni estrategias desde HTTP. El cálculo no implica reserva de stock.
 */
@Service
public class CheckoutQuoteService {
    private final CartRepository carts;
    private final CheckoutTotalsCalculator calculator;

    public CheckoutQuoteService(
            CartRepository carts, CheckoutTotalsCalculator calculator) {
        this.carts = Objects.requireNonNull(carts);
        this.calculator = Objects.requireNonNull(calculator);
    }

    @Transactional(readOnly = true)
    public CheckoutQuoteResponse quote(long userId) {
        if (userId < 1) {
            throw new IllegalArgumentException("Usuario autenticado inválido");
        }
        List<CartLine> current = carts.findItems(userId);
        var totals = calculator.calculate(current);
        long items = current.stream().mapToLong(CartLine::quantity).sum();
        return new CheckoutQuoteResponse(
                items, totals, calculator.shippingPolicy(), true);
    }
}
