package com.techstore.orders.query;

import com.techstore.orders.dto.OrderResponse;

/** El detalle de cliente existente, con propietario solo para ROLE_ADMIN. */
public record AdminOrderDetail(long ownerUserId, OrderResponse order) { }
