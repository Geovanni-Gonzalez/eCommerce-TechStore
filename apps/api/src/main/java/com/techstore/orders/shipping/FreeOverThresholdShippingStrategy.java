package com.techstore.orders.shipping;

import com.techstore.orders.domain.CrcMoney;
import java.math.BigDecimal;

/**
 * Strategy concreta: tarifa fija por debajo del umbral, gratuita desde él.
 * Se usa compareTo para no depender de la escala de BigDecimal.
 */
public final class FreeOverThresholdShippingStrategy implements ShippingStrategy {
    private final BigDecimal feeCrc;
    private final BigDecimal thresholdCrc;

    public FreeOverThresholdShippingStrategy(
            BigDecimal feeCrc, BigDecimal thresholdCrc) {
        this.feeCrc = CrcMoney.require(feeCrc, 14, "shippingFeeCrc");
        this.thresholdCrc = CrcMoney.require(
                thresholdCrc, 14, "freeShippingThresholdCrc");
    }

    @Override
    public String code() {
        return "FREE_OVER_THRESHOLD";
    }

    @Override
    public BigDecimal calculateShippingCrc(BigDecimal subtotalCrc) {
        BigDecimal subtotal = CrcMoney.require(subtotalCrc, 14, "subtotalCrc");
        return subtotal.compareTo(thresholdCrc) >= 0
                ? BigDecimal.ZERO.setScale(2) : feeCrc;
    }
}
