package com.techstore.orders.controller;

import com.techstore.orders.service.OrderReservationService;
import com.techstore.users.security.SessionPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cliente propietario: cancelar pedido PENDING y liberar stock. */
@RestController
@RequestMapping("/api/orders")
public class OrderCancellationController {
    private final OrderReservationService lifecycle;

    public OrderCancellationController(OrderReservationService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId) {
        lifecycle.cancelOwned(principal.id(), orderId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
