package com.techstore.orders.pricing;

import com.techstore.cart.domain.CartLine;
import com.techstore.orders.domain.CrcMoney;
import com.techstore.orders.domain.OrderTotals;
import com.techstore.orders.shipping.ShippingStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Calculador puro (GRASP Information Expert). No confía en importes HTTP:
 * consume CartLine leído del catálogo actual. No reserva inventario.
 *
 * Regla académica explícita: impuesto = subtotal * tasa configurable,
 * redondeado HALF_UP a dos decimales; envío no forma parte de la base imponible.
 * NO constituye asesoría tributaria ni cálculo fiscal productivo.
 */
public final class CheckoutTotalsCalculator {
    private final ShippingStrategy shipping;
    private final BigDecimal taxRate;

    public CheckoutTotalsCalculator(
            ShippingStrategy shipping, BigDecimal taxRate) {
        this.shipping = Objects.requireNonNull(shipping, "shipping");
        if (taxRate == null || taxRate.signum() < 0
                || taxRate.compareTo(BigDecimal.ONE) > 0
                || taxRate.scale() > 4) {
            throw new IllegalArgumentException(
                    "taxRate debe estar entre 0 y 1, hasta cuatro decimales");
        }
        this.taxRate = taxRate;
    }

    public OrderTotals calculate(List<CartLine> cartLines) {
        Objects.requireNonNull(cartLines, "cartLines");
        if (cartLines.isEmpty()) {
            throw new EmptyCheckoutCartException();
        }
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (CartLine line : cartLines) {
            if (line == null || line.productId() < 1
                    || line.quantity() < 1 || line.quantity() > 99
                    || !line.active() || line.stock() < line.quantity()) {
                throw new UnavailableCheckoutItemException();
            }
            BigDecimal price = CrcMoney.require(
                    line.unitPriceCrc(), 12, "unitPriceCrc");
            BigDecimal lineTotal = CrcMoney.require(
                    price.multiply(BigDecimal.valueOf(line.quantity())),
                    14, "lineTotalCrc");
            subtotal = CrcMoney.require(
                    subtotal.add(lineTotal), 14, "subtotalCrc");
        }

        BigDecimal shippingCrc = CrcMoney.require(
                shipping.calculateShippingCrc(subtotal), 14, "shippingCrc");
        BigDecimal taxCrc = CrcMoney.require(
                subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP),
                14, "taxCrc");
        BigDecimal total = CrcMoney.require(
                subtotal.add(shippingCrc).add(taxCrc), 14, "totalCrc");
        return new OrderTotals(subtotal, shippingCrc, taxCrc, total);
    }

    public String shippingPolicy() {
        return shipping.code();
    }
}
