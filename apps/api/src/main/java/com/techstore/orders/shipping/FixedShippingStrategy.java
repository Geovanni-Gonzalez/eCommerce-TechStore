package com.techstore.orders.shipping;

import com.techstore.orders.domain.CrcMoney;
import java.math.BigDecimal;

/** Strategy concreta: tarifa fija, independientemente del subtotal. */
public final class FixedShippingStrategy implements ShippingStrategy {
    private final BigDecimal feeCrc;

    public FixedShippingStrategy(BigDecimal feeCrc) {
        this.feeCrc = CrcMoney.require(feeCrc, 14, "shippingFeeCrc");
    }

    @Override
    public String code() {
        return "FIXED";
    }

    @Override
    public BigDecimal calculateShippingCrc(BigDecimal subtotalCrc) {
        CrcMoney.require(subtotalCrc, 14, "subtotalCrc");
        return feeCrc;
    }
}
