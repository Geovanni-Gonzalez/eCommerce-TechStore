package com.techstore.orders.query;

import com.techstore.orders.domain.OrderStatus;
import com.techstore.orders.domain.OrderTotals;
import com.techstore.orders.domain.PaymentMode;
import java.time.OffsetDateTime;

/** Solo campos comerciales mínimos: sin dirección, propietario ni PII. */
public record OrderSummary(
        long id, OrderStatus status, PaymentMode paymentMode,
        OrderTotals totals, OffsetDateTime createdAt
) { }
