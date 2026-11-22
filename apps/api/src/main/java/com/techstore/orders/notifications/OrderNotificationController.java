package com.techstore.orders.notifications;

import com.techstore.orders.repository.OrderRepository;
import com.techstore.orders.service.OrderNotFoundException;
import com.techstore.users.security.SessionPrincipal;
import java.util.List;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El ownership se verifica primero y también se impone en el SQL. */
@RestController
@RequestMapping("/api/orders")
public class OrderNotificationController {
    private final OrderRepository orders;
    private final OrderNotificationRepository notifications;

    public OrderNotificationController(OrderRepository orders,
            OrderNotificationRepository notifications) {
        this.orders = Objects.requireNonNull(orders);
        this.notifications = Objects.requireNonNull(notifications);
    }

    @GetMapping(path = "/{orderId}/notifications",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OrderNotification>> list(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId) {
        orders.findByIdForUser(principal.id(), orderId)
                .orElseThrow(OrderNotFoundException::new);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(notifications.listForOwner(principal.id(), orderId));
    }
}
