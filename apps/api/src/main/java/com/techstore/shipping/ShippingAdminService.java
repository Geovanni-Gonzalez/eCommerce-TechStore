package com.techstore.shipping;

import com.techstore.orders.domain.OrderStatus;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BKL-047: no crea orden, no autoriza pago, no altera stock.
 * Bloquea orders primero, igual que cancelación y conciliación BKL-040/045.
 */
@Service
public class ShippingAdminService {
    private final JdbcShipmentRepository repository;
    private final ShippingGateway gateway;

    public ShippingAdminService(JdbcShipmentRepository repository, ShippingGateway gateway) {
        this.repository=Objects.requireNonNull(repository);
        this.gateway=Objects.requireNonNull(gateway);
    }

    @Transactional
    public ShippingRegistration register(long actorId,long orderId) {
        if(actorId<1||orderId<1) {
            throw new IllegalArgumentException("IDs inválidos");
        }
        OrderStatus status=repository.lockOrderStatus(orderId)
                .orElseThrow(ShippingNotFoundException::new);
        if(status!=OrderStatus.CONFIRMED) {
            throw new ShippingOrderNotConfirmedException();
        }
        return gateway.register(new ShippingRegistrationCommand(orderId,actorId));
    }

    /** No cambia orders, pagos ni stock; la auditoría SQL es atómica. */
    @Transactional
    public ShipmentRecord advance(long actorId,long orderId,ShippingStatus target) {
        if(actorId<1||orderId<1||target==null)
            throw new IllegalArgumentException("Solicitud logística inválida");
        OrderStatus orderStatus=repository.lockOrderStatus(orderId)
                .orElseThrow(ShippingNotFoundException::new);
        if(orderStatus!=OrderStatus.CONFIRMED)
            throw new ShippingOrderNotConfirmedException();
        ShipmentRecord record=repository.findByOrderId(orderId)
                .orElseThrow(ShippingNotFoundException::new);
        if(record.status()==target)return record; // reintento sin duplicar hito
        return gateway.advance(new ShippingTransitionCommand(
                orderId,actorId,record.status(),target));
    }

    @Transactional(readOnly=true)
    public java.util.List<ShipmentHistoryEntry> history(long orderId) {
        if(orderId<1)throw new IllegalArgumentException("ID inválido");
        repository.findByOrderId(orderId)
                .orElseThrow(ShippingNotFoundException::new);
        return repository.findHistoryByOrderId(orderId);
    }

    @Transactional(readOnly=true)
    public ShipmentRecord findByOrderId(long orderId) {
        if(orderId<1)throw new IllegalArgumentException("ID inválido");
        return repository.findByOrderId(orderId)
                .orElseThrow(ShippingNotFoundException::new);
    }
}
