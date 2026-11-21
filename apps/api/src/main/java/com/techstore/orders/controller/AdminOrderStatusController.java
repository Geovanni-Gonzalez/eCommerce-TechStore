package com.techstore.orders.controller;

import com.techstore.orders.history.OrderStatusHistoryEntry;
import com.techstore.orders.service.OrderNotCancellableException;
import com.techstore.orders.service.OrderReservationService;
import com.techstore.users.security.SessionPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BKL-045: ADMIN cancela PENDING y consulta trazabilidad de transiciones.
 * CONFIRMED solo procede del conciliador BKL-040, no de esta ruta ADMIN.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderStatusController {
    private final OrderReservationService orders;

    public AdminOrderStatusController(OrderReservationService orders) {
        this.orders = Objects.requireNonNull(orders);
    }

    @PatchMapping(path = "/{orderId}/status",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> change(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId, @RequestBody Map<String, Object> body) {
        if (body == null || body.size() != 1
                || !(body.get("status") instanceof String desired)
                || !List.of("PENDING", "CONFIRMED", "CANCELLED").contains(desired)) {
            throw new InvalidOrderStatusRequestException();
        }
        if (!"CANCELLED".equals(desired)) {
            throw new OrderNotCancellableException();
        }
        orders.cancelAsAdmin(principal.id(), orderId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping(path = "/{orderId}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OrderStatusHistoryEntry>> history(
            @PathVariable long orderId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(orders.historyForAdmin(orderId));
    }
}
