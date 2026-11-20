package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.domain.CartLine;
import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.pricing.CheckoutTotalsCalculator;
import com.techstore.orders.pricing.EmptyCheckoutCartException;
import com.techstore.orders.service.CheckoutQuoteService;
import com.techstore.orders.shipping.FixedShippingStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutQuoteServiceTest {

    @Test
    void readsOnlyAuthenticatedUserCartAndDerivesAllAmountsOnServer() {
        var repository = new FakeCartRepo();
        var service = new CheckoutQuoteService(repository,
                new CheckoutTotalsCalculator(
                        new FixedShippingStrategy(new BigDecimal("500.00")),
                        new BigDecimal("0.13")));
        var result = service.quote(10);
        assertEquals(10, repository.requestedUser);
        assertEquals(2, result.totalItems());
        assertEquals(new BigDecimal("2500.00"), result.totals().subtotalCrc());
        assertEquals(new BigDecimal("500.00"), result.totals().shippingCrc());
        assertEquals(new BigDecimal("325.00"), result.totals().taxCrc());
        assertEquals(new BigDecimal("3325.00"), result.totals().totalCrc());
        assertTrue(result.estimateOnly());
        assertEquals("FIXED", result.shippingPolicy());
    }

    @Test
    void otherUserCannotReceiveSomeoneElsesCart() {
        var repository = new FakeCartRepo();
        var service = new CheckoutQuoteService(repository,
                new CheckoutTotalsCalculator(
                        new FixedShippingStrategy(BigDecimal.ZERO),
                        BigDecimal.ZERO));
        assertThrows(EmptyCheckoutCartException.class, () -> service.quote(99));
        assertEquals(99, repository.requestedUser);
    }

    @Test
    void invalidPrincipalCannotQueryCart() {
        var repository = new FakeCartRepo();
        var service = new CheckoutQuoteService(repository,
                new CheckoutTotalsCalculator(
                        new FixedShippingStrategy(BigDecimal.ZERO),
                        BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.quote(0));
        assertEquals(0, repository.requestedUser);
    }

    private static final class FakeCartRepo implements CartRepository {
        private long requestedUser;
        @Override
        public List<CartLine> findItems(long userId) {
            requestedUser = userId;
            return userId == 10 ? List.of(new CartLine(
                    1, "SKU", "Teclado", new BigDecimal("1250.00"),
                    2, 7, true)) : List.of();
        }
        @Override public boolean containsProduct(long userId, long productId) {
            throw new UnsupportedOperationException();
        }
        @Override public void ensureCart(long userId) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean incrementProduct(
                long userId, long productId, int quantity) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean setQuantity(
                long userId, long productId, int quantity) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean removeProduct(long userId, long productId) {
            throw new UnsupportedOperationException();
        }
        @Override public void clear(long userId) {
            throw new UnsupportedOperationException();
        }
    }
}
