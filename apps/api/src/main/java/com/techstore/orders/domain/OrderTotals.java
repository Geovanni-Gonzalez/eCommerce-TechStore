package com.techstore.orders.domain;

import java.math.BigDecimal;

/** Importes definitivos del snapshot; no se aceptan del cliente vía HTTP. */
public record OrderTotals(
        BigDecimal subtotalCrc,
        BigDecimal shippingCrc,
        BigDecimal taxCrc,
        BigDecimal totalCrc
) {
    public OrderTotals {
        subtotalCrc = CrcMoney.require(subtotalCrc, 14, "subtotalCrc");
        shippingCrc = CrcMoney.require(shippingCrc, 14, "shippingCrc");
        taxCrc = CrcMoney.require(taxCrc, 14, "taxCrc");
        totalCrc = CrcMoney.require(totalCrc, 14, "totalCrc");
        if (totalCrc.compareTo(subtotalCrc.add(shippingCrc).add(taxCrc)) != 0) {
            throw new IllegalArgumentException(
                    "totalCrc debe coincidir con subtotal, envío e impuestos");
        }
    }
}
