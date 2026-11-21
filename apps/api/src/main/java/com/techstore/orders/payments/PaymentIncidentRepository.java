package com.techstore.orders.payments;

import java.util.List;

/** Mantener el mismo commit que el evento o la decisión de vencimiento. */
public interface PaymentIncidentRepository {
    void record(long orderId, PaymentIncidentReason reason, Long eventId);
    List<PaymentIncidentView> findForOrder(long orderId);
}
