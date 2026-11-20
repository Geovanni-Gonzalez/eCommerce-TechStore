package com.techstore.cart.dto;

import com.techstore.cart.domain.CartLine;
import java.math.BigDecimal;

public record CartItemResponse(
        long productId, String sku, String name, int quantity, int stock,
        boolean available, BigDecimal unitPriceCrc, BigDecimal lineSubtotalCrc
) {
    public static CartItemResponse from(CartLine line) {
        return new CartItemResponse(
                line.productId(), line.sku(), line.name(), line.quantity(),
                line.stock(), line.active() && line.stock() >= line.quantity(),
                line.unitPriceCrc(),
                line.unitPriceCrc().multiply(BigDecimal.valueOf(line.quantity())));
    }
}
