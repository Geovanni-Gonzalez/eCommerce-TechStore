package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.cart.domain.CartLine;
import com.techstore.orders.pricing.CheckoutPricingConfig;
import com.techstore.orders.pricing.CheckoutTotalsCalculator;
import com.techstore.orders.pricing.EmptyCheckoutCartException;
import com.techstore.orders.pricing.UnavailableCheckoutItemException;
import com.techstore.orders.shipping.FixedShippingStrategy;
import com.techstore.orders.shipping.FreeOverThresholdShippingStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutTotalsCalculatorTest {

    private static BigDecimal crc(String value) {
        return new BigDecimal(value);
    }

    private static CartLine line(String unitPrice, int quantity, int stock, boolean active) {
        return new CartLine(3, "TEC-TEST", "Mouse", crc(unitPrice),
                quantity, stock, active);
    }

    @Test
    void fixedStrategyAppliesConfiguredFeeAndCalculatesTax() {
        var calc = new CheckoutTotalsCalculator(
                new FixedShippingStrategy(crc("2500.00")), crc("0.13"));
        var result = calc.calculate(List.of(line("1000.00", 2, 5, true)));
        assertEquals(crc("2000.00"), result.subtotalCrc());
        assertEquals(crc("2500.00"), result.shippingCrc());
        assertEquals(crc("260.00"), result.taxCrc());
        assertEquals(crc("4760.00"), result.totalCrc());
        assertEquals("FIXED", calc.shippingPolicy());
    }

    @Test
    void freeThresholdStrategyChargesBelowThresholdAndIsFreeAtBoundary() {
        var strategy = new FreeOverThresholdShippingStrategy(
                crc("2500.00"), crc("30000.00"));
        var calc = new CheckoutTotalsCalculator(strategy, crc("0.13"));
        var below = calc.calculate(List.of(line("14999.99", 2, 5, true)));
        assertEquals(crc("2500.00"), below.shippingCrc());
        var at = calc.calculate(List.of(line("15000.00", 2, 5, true)));
        assertEquals(crc("0.00"), at.shippingCrc());
        assertEquals(crc("3900.00"), at.taxCrc());
        assertEquals(crc("33900.00"), at.totalCrc());
        assertEquals("FREE_OVER_THRESHOLD", calc.shippingPolicy());
    }

    @Test
    void taxRoundsHalfUpToTwoDecimals() {
        var calc = new CheckoutTotalsCalculator(
                new FixedShippingStrategy(crc("0.00")), crc("0.13"));
        var result = calc.calculate(List.of(line("0.50", 1, 2, true)));
        // 0.50 * 0.13 = 0.065; en el prototipo se redondea HALF_UP.
        assertEquals(crc("0.07"), result.taxCrc());
        assertEquals(crc("0.57"), result.totalCrc());
    }

    @Test
    void taxIsComputedFromSubtotalNotFromShippingFee() {
        var calc = new CheckoutTotalsCalculator(
                new FixedShippingStrategy(crc("1000.00")), crc("0.13"));
        var result = calc.calculate(List.of(line("1000.00", 1, 3, true)));
        assertEquals(crc("130.00"), result.taxCrc());
        assertEquals(crc("2130.00"), result.totalCrc());
    }

    @Test
    void emptyCartCannotBeQuoted() {
        var calc = new CheckoutTotalsCalculator(
                new FixedShippingStrategy(crc("100.00")), BigDecimal.ZERO);
        assertThrows(EmptyCheckoutCartException.class,
                () -> calc.calculate(List.of()));
    }

    @Test
    void inactiveAndUnderstockedItemsPreventQuote() {
        var calc = new CheckoutTotalsCalculator(
                new FixedShippingStrategy(crc("0.00")), crc("0.13"));
        assertThrows(UnavailableCheckoutItemException.class,
                () -> calc.calculate(List.of(line("100.00", 2, 2, false))));
        assertThrows(UnavailableCheckoutItemException.class,
                () -> calc.calculate(List.of(line("100.00", 2, 1, true))));
        assertThrows(UnavailableCheckoutItemException.class,
                () -> calc.calculate(List.of(line("100.00", 0, 5, true))));
    }

    @Test
    void invalidPricesAndMonetaryOverflowAreRejected() {
        var calc = new CheckoutTotalsCalculator(
                new FixedShippingStrategy(crc("0.00")), crc("0.13"));
        assertThrows(IllegalArgumentException.class,
                () -> calc.calculate(List.of(line("-1.00", 1, 2, true))));
        assertThrows(IllegalArgumentException.class,
                () -> calc.calculate(List.of(line("100.123", 1, 2, true))));
        // Cada línea cabe en NUMERIC(14,2), pero su suma no.
        assertThrows(IllegalArgumentException.class,
                () -> calc.calculate(List.of(
                        line("9999999999.99", 99, 99, true),
                        new CartLine(4, "TEC-2", "Cable", crc("9999999999.99"),
                                99, 99, true))));
    }

    @Test
    void rejectsInvalidTaxRatesAndShippingParameters() {
        var fixed = new FixedShippingStrategy(crc("0.00"));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckoutTotalsCalculator(fixed, crc("-0.1")));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckoutTotalsCalculator(fixed, crc("1.01")));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckoutTotalsCalculator(fixed, crc("0.12345")));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedShippingStrategy(crc("-1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new FreeOverThresholdShippingStrategy(
                        crc("100.00"), crc("-1.00")));
    }

    @Test
    void strategyIsInterchangeableWithoutChangingTheCalculator() {
        var flat = CheckoutPricingConfig.selectPolicy(
                "fixed", crc("2500.00"), crc("30000.00"));
        var threshold = CheckoutPricingConfig.selectPolicy(
                "FREE_OVER_THRESHOLD", crc("2500.00"), crc("30000.00"));
        var lines = List.of(line("15000.00", 2, 3, true));
        assertEquals(crc("2500.00"),
                new CheckoutTotalsCalculator(flat, crc("0.13"))
                        .calculate(lines).shippingCrc());
        assertEquals(crc("0.00"),
                new CheckoutTotalsCalculator(threshold, crc("0.13"))
                        .calculate(lines).shippingCrc());
        assertThrows(IllegalArgumentException.class,
                () -> CheckoutPricingConfig.selectPolicy(
                        "NO_EXISTE", crc("2500.00"), crc("30000.00")));
    }

    @Test
    void sumOfMultipleLinesUsesIntegerQuantitiesAndExactBigDecimal() {
        var calc = new CheckoutTotalsCalculator(
                new FixedShippingStrategy(crc("0.00")), BigDecimal.ZERO);
        var items = List.of(
                line("125.25", 2, 5, true),
                new CartLine(4, "TEC-2", "Cable", crc("400.10"), 3, 5, true));
        var totals = calc.calculate(items);
        assertEquals(crc("1450.80"), totals.subtotalCrc());
        assertEquals(crc("1450.80"), totals.totalCrc());
    }
}
