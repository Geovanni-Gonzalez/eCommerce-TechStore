package com.techstore.orders.shipping;

import java.math.BigDecimal;

/**
 * GoF Strategy: algoritmo intercambiable para el costo de envío.
 * Se elige por configuración del servidor, nunca por una entrada del cliente.
 */
public interface ShippingStrategy {
    String code();
    BigDecimal calculateShippingCrc(BigDecimal subtotalCrc);
}
