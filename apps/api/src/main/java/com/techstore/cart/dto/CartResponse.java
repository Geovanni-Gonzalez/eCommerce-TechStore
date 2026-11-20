package com.techstore.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Subtotal estimado según precio actual del catálogo, SIN impuestos/envío.
 * Un producto no disponible sigue visible para que el cliente pueda quitarlo.
 * Checkout debe recalcular y validar antes de crear el pedido (BKL-033).
 */
public record CartResponse(
        List<CartItemResponse> items,
        long totalItems,
        BigDecimal subtotalCrc,
        boolean checkoutReady
) { }
