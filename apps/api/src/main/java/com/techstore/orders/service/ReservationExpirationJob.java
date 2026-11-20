package com.techstore.orders.service;

import com.techstore.orders.inventory.OrderReservationLifecycleRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expira lotes de pedidos; un error no cancela las otras operaciones. */
@Component
@ConditionalOnProperty(name="techstore.checkout.reservation-expiry-enabled",
        havingValue="true", matchIfMissing=true)
public class ReservationExpirationJob {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationExpirationJob.class);
    private final OrderReservationLifecycleRepository orders;
    private final OrderReservationService lifecycle;

    public ReservationExpirationJob(OrderReservationLifecycleRepository orders,
            OrderReservationService lifecycle) {
        this.orders = Objects.requireNonNull(orders);
        this.lifecycle = Objects.requireNonNull(lifecycle);
    }

    @Scheduled(fixedDelayString = "${techstore.checkout.reservation-expiry-scan-ms:60000}")
    public void expirePending() {
        for (Long id : orders.findExpiredOrders(100)) {
            try {
                lifecycle.expireOne(id);
            } catch (RuntimeException failure) {
                LOG.error("Falló liberación de pedido vencido {}", id, failure);
            }
        }
    }
}
