package com.techstore.orders.service;

/** Un pedido no pendiente no admite cancelación por el cliente. */
public class OrderNotCancellableException extends RuntimeException { }
