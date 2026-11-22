package com.techstore.shipping;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** WHERE user_id=? en SQL: pedido ajeno e inexistente son 404 idénticos. */
@Service
public class ShippingCustomerService {
    private final JdbcShipmentRepository shipments;
    public ShippingCustomerService(JdbcShipmentRepository shipments) {
        this.shipments=Objects.requireNonNull(shipments);
    }
    @Transactional(readOnly=true)
    public ShippingTrackingView tracking(long customerId,long orderId) {
        if(customerId<1||orderId<1)throw new ShippingNotFoundException();
        if(shipments.findOwnedOrderStatus(customerId,orderId).isEmpty())
            throw new ShippingNotFoundException();
        var record=shipments.findByOrderId(orderId);
        if(record.isEmpty())
            return new ShippingTrackingView(orderId,"NOT_REGISTERED",null,List.of());
        var events=shipments.findHistoryByOrderId(orderId).stream()
                .map(e->new ShippingCustomerEvent(
                        e.toStatus(),e.reason(),e.changedAt())).toList();
        return new ShippingTrackingView(orderId,record.get().status().name(),
                record.get().registeredAt(),events);
    }
}
