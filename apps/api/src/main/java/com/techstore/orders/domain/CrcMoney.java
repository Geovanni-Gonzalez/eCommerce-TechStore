package com.techstore.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valores CRC exactos: nunca float/double ni redondeos silenciosos.
 * precision 12: precio por unidad; precision 14: línea y total del pedido.
 */
public final class CrcMoney {
    private CrcMoney() { }

    public static BigDecimal require(
            BigDecimal value, int precision, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " debe ser un monto no negativo");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    field + " admite como máximo dos decimales", exception);
        }
        if (normalized.precision() > precision) {
            throw new IllegalArgumentException(field + " excede la capacidad NUMERIC");
        }
        return normalized;
    }
}
