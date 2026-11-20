package com.techstore.orders;

import static org.junit.jupiter.api.Assertions.*;

import com.techstore.orders.domain.CrcMoney;
import com.techstore.orders.domain.OrderDraft;
import com.techstore.orders.domain.OrderItemSnapshot;
import com.techstore.orders.domain.OrderTotals;
import com.techstore.orders.domain.ShippingAddress;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderDomainTest {

    private static ShippingAddress address() {
        return new ShippingAddress(" San José ", " Central ", " Carmen ",
                " Frente a la plaza central ", " Ana Pérez ", " 8888-1111 ");
    }

    private static OrderTotals totals() {
        return new OrderTotals(new BigDecimal("25980.00"),
                new BigDecimal("2000.00"), new BigDecimal("0.00"),
                new BigDecimal("27980.00"));
    }

    private static OrderItemSnapshot item() {
        return new OrderItemSnapshot(2, " Teclado mecánico ", 2,
                new BigDecimal("12990.00"));
    }

    @Test
    void shippingAddressNormalizesFields() {
        var saved = address();
        assertEquals("San José", saved.province());
        assertEquals("Ana Pérez", saved.recipientName());
        assertEquals("8888-1111", saved.phone());
        assertEquals("Frente a la plaza central", saved.details());
    }

    @Test
    void shippingAddressRejectsMissingInvalidOrControlFields() {
        assertThrows(IllegalArgumentException.class, () -> new ShippingAddress(
                "", "Central", "Carmen", "Dirección válida", "Ana Pérez", "88881111"));
        assertThrows(IllegalArgumentException.class, () -> new ShippingAddress(
                "San José", "Central", "Carmen", "A", "Ana Pérez", "88881111"));
        assertThrows(IllegalArgumentException.class, () -> new ShippingAddress(
                "San José", "Central", "Carmen", "Dirección\n inválida", "Ana Pérez", "88881111"));
        assertThrows(IllegalArgumentException.class, () -> new ShippingAddress(
                "San José", "Central", "Carmen", "Dirección válida", "Ana Pérez", "123"));
    }

    @Test
    void snapshotRetainsNamePriceAndComputedTotal() {
        var line = item();
        assertEquals("Teclado mecánico", line.name());
        assertEquals(new BigDecimal("12990.00"), line.unitPriceCrc());
        assertEquals(new BigDecimal("25980.00"), line.lineTotalCrc());
    }

    @Test
    void snapshotRejectsInvalidQuantityIdentifierAndName() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderItemSnapshot(0, "Mouse", 1, new BigDecimal("1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderItemSnapshot(1, "Mouse", 0, new BigDecimal("1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderItemSnapshot(1, "Mouse", 100, new BigDecimal("1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderItemSnapshot(1, " ", 1, new BigDecimal("1.00")));
    }

    @Test
    void moneyRejectsNegativePrecisionLossAndOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> CrcMoney.require(new BigDecimal("-1.00"), 12, "precio"));
        assertThrows(IllegalArgumentException.class,
                () -> CrcMoney.require(new BigDecimal("1.234"), 12, "precio"));
        assertThrows(IllegalArgumentException.class,
                () -> CrcMoney.require(new BigDecimal("10000000000.00"), 12, "precio"));
        assertEquals(new BigDecimal("1.00"),
                CrcMoney.require(new BigDecimal("1"), 12, "precio"));
    }

    @Test
    void totalsRequireExactSumAndNonnegativeAmounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderTotals(new BigDecimal("10.00"),
                        new BigDecimal("2.00"), new BigDecimal("1.00"),
                        new BigDecimal("14.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderTotals(new BigDecimal("10.00"),
                        new BigDecimal("-1.00"), BigDecimal.ZERO, new BigDecimal("9.00")));
    }

    @Test
    void draftRejectsWrongSubtotalDuplicateProductsAndMissingLines() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderDraft(1, address(), List.of(item()),
                        new OrderTotals(BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderDraft(1, address(), List.of(item(), item()), totals()));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderDraft(1, address(), List.of(), totals()));
    }

    @Test
    void draftDoesNotAcceptInvalidUser() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderDraft(0, address(), List.of(item()), totals()));
    }

    @Test
    void draftDefensivelyCopiesItsList() {
        var mutable = new ArrayList<OrderItemSnapshot>();
        mutable.add(item());
        var draft = new OrderDraft(9, address(), mutable, totals());
        mutable.clear();
        assertEquals(1, draft.items().size());
        assertThrows(UnsupportedOperationException.class, () -> draft.items().clear());
    }
}
