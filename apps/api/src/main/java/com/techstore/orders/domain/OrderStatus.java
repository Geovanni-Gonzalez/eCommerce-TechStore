package com.techstore.orders.domain;

/** Estados permitidos por V6; sus transiciones quedan para BKL-045. */
public enum OrderStatus {
    PENDING, CONFIRMED, CANCELLED
}
