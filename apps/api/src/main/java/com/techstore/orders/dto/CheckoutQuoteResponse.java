package com.techstore.orders.dto;

import com.techstore.orders.domain.OrderTotals;

/**
 * Cotización informativa: NO confirma precio ni crea un pedido.
 * El checkout futuro debe validar de nuevo dentro de una transacción.
 */
public record CheckoutQuoteResponse(
        long totalItems,
        OrderTotals totals,
        String shippingPolicy,
        boolean estimateOnly
) { }
