package com.techstore.cart;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.domain.CartLine;
import com.techstore.cart.dto.CartItemRequest;
import com.techstore.cart.repository.CartRepository;
import com.techstore.cart.service.CartConflictException;
import com.techstore.cart.service.CartItemNotFoundException;
import com.techstore.cart.service.CartProductNotFoundException;
import com.techstore.cart.service.CartService;
import com.techstore.cart.service.CartValidationException;
import com.techstore.catalog.domain.Product;
import com.techstore.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CartServiceTest {

    private final FakeCarts carts = new FakeCarts();
    private final FakeProducts products = new FakeProducts();
    private final CartService service = new CartService(carts, products);

    @Test
    void emptyCartHasZeroSubtotalAndIsNotReady() {
        var cart = service.getCart(4);
        assertTrue(cart.items().isEmpty());
        assertEquals(0, cart.totalItems());
        assertEquals(0, cart.subtotalCrc().compareTo(BigDecimal.ZERO));
        assertFalse(cart.checkoutReady());
    }

    @Test
    void addAndIncrementComputePricesOnlyFromServer() {
        service.addItem(4, 1, 2);
        var cart = service.addItem(4, 1, 3);
        assertEquals(5, cart.totalItems());
        assertEquals(1, cart.items().size());
        assertEquals(0, cart.subtotalCrc().compareTo(new BigDecimal("64950.00")));
        assertTrue(cart.checkoutReady());
    }

    @Test
    void userCannotReadOrChangeAnotherCart() {
        service.addItem(4, 1, 1);
        assertTrue(service.getCart(8).items().isEmpty());
        assertThrows(CartItemNotFoundException.class, () -> service.setQuantity(8, 1, 2));
        service.removeItem(8, 1);
        assertEquals(1, service.getCart(4).totalItems());
    }

    @Test
    void stockAndMaximumQuantityAreEnforced() {
        assertThrows(CartConflictException.class, () -> service.addItem(4, 1, 11));
        assertThrows(CartValidationException.class, () -> service.addItem(4, 1, 0));
        assertThrows(CartValidationException.class, () -> service.addItem(4, 1, 100));
        service.addItem(4, 1, 9);
        assertThrows(CartConflictException.class, () -> service.addItem(4, 1, 2));
        assertEquals(9, service.getCart(4).totalItems());
    }

    @Test
    void disabledAndUnknownProductsCannotBeAdded() {
        products.active = false;
        assertThrows(CartProductNotFoundException.class, () -> service.addItem(4, 1, 1));
        assertThrows(CartProductNotFoundException.class, () -> service.addItem(4, 99, 1));
    }

    @Test
    void unavailableItemsRemainVisibleAndEditableByRemoval() {
        service.addItem(4, 1, 2);
        products.active = false;
        assertFalse(service.getCart(4).checkoutReady());
        assertFalse(service.getCart(4).items().getFirst().available());
        service.removeItem(4, 1);
        assertTrue(service.getCart(4).items().isEmpty());
    }

    @Test
    void overwriteQuantityAndClearAreIdempotent() {
        service.addItem(4, 1, 2);
        assertEquals(4, service.setQuantity(4, 1, 4).totalItems());
        service.clear(4);
        service.clear(4);
        assertTrue(service.getCart(4).items().isEmpty());
    }

    @Test
    void jsonParserRejectsRolePriceAndInvalidTypes() {
        assertThrows(CartValidationException.class, () -> CartItemRequest.create(
                Map.of("productId", 1, "quantity", 1, "userId", 8)));
        assertThrows(CartValidationException.class, () -> CartItemRequest.create(
                Map.of("productId", 1, "quantity", 1, "priceCrc", 10)));
        assertThrows(CartValidationException.class, () -> CartItemRequest.create(
                Map.of("productId", 1, "quantity", 1.5)));
        assertThrows(CartValidationException.class, () -> CartItemRequest.updateQuantity(
                Map.of("quantity", 0)));
        assertEquals(2, CartItemRequest.updateQuantity(Map.of("quantity", 2)));
    }

    private static final class FakeProducts implements ProductRepository {
        boolean active = true;
        @Override public List<Product> findActivePage(int limit, long offset) {
            return List.of();
        }
        @Override public Optional<Product> findActiveById(long id) {
            return id == 1 && active ? Optional.of(
                    new Product(1L, null, "TEST", "Mouse", "",
                            new BigDecimal("12990.00"), 10, true)) : Optional.empty();
        }
        @Override public long countActive() { return 0; }
    }

    private static final class FakeCarts implements CartRepository {
        private final Map<Long, Map<Long, Integer>> data = new HashMap<>();
        @Override public List<CartLine> findItems(long userId) {
            List<CartLine> lines = new ArrayList<>();
            data.getOrDefault(userId, Map.of()).forEach((id, quantity) ->
                    lines.add(new CartLine(id, "TEST", "Mouse",
                            new BigDecimal("12990.00"), quantity, 10, true)));
            return lines;
        }
        @Override public boolean containsProduct(long userId, long productId) {
            return data.getOrDefault(userId, Map.of()).containsKey(productId);
        }
        @Override public void ensureCart(long userId) {
            data.computeIfAbsent(userId, ignored -> new HashMap<>());
        }
        @Override public boolean incrementProduct(long userId, long productId, int quantity) {
            var items = data.get(userId);
            int old = items.getOrDefault(productId, 0);
            if (old + quantity > 10 || old + quantity > 99) return false;
            items.put(productId, old + quantity);
            return true;
        }
        @Override public boolean setQuantity(long userId, long productId, int quantity) {
            if (!containsProduct(userId, productId) || quantity > 10) return false;
            data.get(userId).put(productId, quantity);
            return true;
        }
        @Override public boolean removeProduct(long userId, long productId) {
            return data.getOrDefault(userId, Map.of()).remove(productId) != null;
        }
        @Override public void clear(long userId) {
            data.remove(userId);
        }
    }
}
