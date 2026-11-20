package com.techstore.orders.events;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * GoF Observer concreto: escucha el evento sin acoplar el checkout a auditoría.
 * Solo observa commits reales; si falla, no falsea el resultado del pedido.
 *
 * La entrega es best effort, NO es outbox ni garantía de recuperación tras crash.
 */
@Component
public class OrderAuditListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderAuditListener.class);
    private final OrderEventAuditRepository audit;

    public OrderAuditListener(OrderEventAuditRepository audit) {
        this.audit = Objects.requireNonNull(audit);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            audit.recordCreated(event);
        } catch (RuntimeException failure) {
            // El pedido ya hizo commit. No devolver 500 ni sugerir un retry
            // que pudiera crear otro pedido; el fallo queda en logs.
            LOG.error("No se pudo auditar ORDER_CREATED para pedido {}", event.orderId(),
                    failure);
        }
    }
}
