package com.techstore.orders.dto;

import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.domain.OrderTotals;
import com.techstore.orders.domain.PaymentMode;
import com.techstore.orders.domain.ShippingAddress;
import java.time.OffsetDateTime;
import java.util.List;

/** DTO público sin el identificador de propietario ni datos de autenticación. */
public record OrderResponse(
        long id, OrderStatus status, PaymentMode paymentMode,
        List<OrderItemResponse> items, ShippingAddress shippingAddress,
        OrderTotals totals, OffsetDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.status(),
                order.paymentMode(),
                order.items().stream().map(OrderItemResponse::from).toList(),
                order.shippingAddress(), order.totals(), order.createdAt());
    }
}
