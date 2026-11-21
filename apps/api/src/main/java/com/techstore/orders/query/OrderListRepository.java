package com.techstore.orders.query;

public interface OrderListRepository {
    OrderPage<OrderSummary> listForOwner(long userId, int limit, Long beforeId);
    OrderPage<AdminOrderSummary> listForAdmin(int limit, Long beforeId);
}
