package com.techstore.orders.dto;

import com.techstore.orders.domain.OrderItemSnapshot;
import java.math.BigDecimal;

public record OrderItemResponse(
        long productId, String name, int quantity,
        BigDecimal unitPriceCrc, BigDecimal lineTotalCrc
) {
    public static OrderItemResponse from(OrderItemSnapshot item) {
        return new OrderItemResponse(
                item.productId(), item.name(), item.quantity(),
                item.unitPriceCrc(), item.lineTotalCrc());
    }
}
