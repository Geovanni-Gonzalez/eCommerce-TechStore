package com.techstore.orders.controller;

import com.techstore.orders.dto.CreateCheckoutOrderRequest;
import com.techstore.orders.dto.OrderResponse;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.users.security.SessionPrincipal;
import java.net.URI;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El controller no calcula totales, no toca SQL ni altera stock. */
@RestController
@RequestMapping("/api/orders")
public final class OrderCheckoutController {
    private final CheckoutFacade checkout;

    public OrderCheckoutController(CheckoutFacade checkout) {
        this.checkout = checkout;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestBody Map<String, Object> body) {
        var parsed = CreateCheckoutOrderRequest.parse(body);
        var order = checkout.confirm(principal.id(), parsed.shippingAddress());
        return ResponseEntity.created(URI.create("/api/orders/" + order.id()))
                .cacheControl(CacheControl.noStore())
                .body(OrderResponse.from(order));
    }
}
