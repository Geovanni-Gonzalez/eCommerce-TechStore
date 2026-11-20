package com.techstore.cart.service;

import com.techstore.cart.dto.CartItemResponse;
import com.techstore.cart.dto.CartResponse;
import com.techstore.cart.repository.CartRepository;
import com.techstore.catalog.domain.Product;
import com.techstore.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso por usuario autenticado; no acepta IDs de carrito ni precios del
 * cliente. El stock es validado, pero NO se reserva hasta el futuro checkout.
 */
@Service
public class CartService {

    private final CartRepository carts;
    private final ProductRepository products;

    public CartService(CartRepository carts, ProductRepository products) {
        this.carts = Objects.requireNonNull(carts);
        this.products = Objects.requireNonNull(products);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(long userId) {
        requireUser(userId);
        List<CartItemResponse> items = carts.findItems(userId).stream()
                .map(CartItemResponse::from).toList();
        long totalItems = items.stream().mapToLong(CartItemResponse::quantity).sum();
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineSubtotalCrc)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        boolean ready = !items.isEmpty()
                && items.stream().allMatch(CartItemResponse::available);
        return new CartResponse(items, totalItems, subtotal, ready);
    }

    @Transactional
    public CartResponse addItem(long userId, long productId, int quantity) {
        requireUser(userId);
        requireProduct(productId);
        requireQuantity(quantity);
        Product product = activeProduct(productId);
        if (quantity > product.stock()) {
            throw new CartConflictException();
        }
        carts.ensureCart(userId);
        if (!carts.incrementProduct(userId, productId, quantity)) {
            throw new CartConflictException();
        }
        return getCart(userId);
    }

    @Transactional
    public CartResponse setQuantity(long userId, long productId, int quantity) {
        requireUser(userId);
        requireProduct(productId);
        requireQuantity(quantity);
        if (!carts.containsProduct(userId, productId)) {
            throw new CartItemNotFoundException();
        }
        Product product = activeProduct(productId);
        if (quantity > product.stock() || !carts.setQuantity(userId, productId, quantity)) {
            throw new CartConflictException();
        }
        return getCart(userId);
    }

    @Transactional
    public void removeItem(long userId, long productId) {
        requireUser(userId);
        requireProduct(productId);
        // DELETE idempotente: incluso un producto que fue desactivado
        // siempre puede eliminarse del carrito.
        carts.removeProduct(userId, productId);
    }

    @Transactional
    public void clear(long userId) {
        requireUser(userId);
        carts.clear(userId);
    }

    private Product activeProduct(long productId) {
        return products.findActiveById(productId)
                .orElseThrow(CartProductNotFoundException::new);
    }

    private static void requireQuantity(int quantity) {
        if (quantity < 1 || quantity > 99) {
            throw new CartValidationException("quantity debe estar entre 1 y 99");
        }
    }

    private static void requireProduct(long productId) {
        if (productId < 1) {
            throw new CartValidationException("productId debe ser positivo");
        }
    }

    private static void requireUser(long userId) {
        if (userId < 1) {
            throw new IllegalArgumentException("El usuario autenticado es inválido");
        }
    }
}
