package com.techstore.orders.query;

/** El identificador del propietario solo aparece en rutas ROLE_ADMIN. */
public record AdminOrderSummary(long ownerUserId, OrderSummary order) { }
