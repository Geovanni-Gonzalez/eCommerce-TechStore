package com.techstore.orders.controller;

import com.techstore.orders.dto.OrderResponse;
import com.techstore.orders.query.OrderPage;
import com.techstore.orders.query.OrderQueryService;
import com.techstore.orders.query.OrderSummary;
import com.techstore.orders.query.OrderTimelineEntry;
import com.techstore.users.security.SessionPrincipal;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exclusivamente CLIENT; ownerId proviene del token Bearer revocable. */
@RestController
@RequestMapping("/api/orders")
public class OrderReadController {
    private final OrderQueryService orders;

    public OrderReadController(OrderQueryService orders) {
        this.orders = orders;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderPage<OrderSummary>> mine(
            @AuthenticationPrincipal SessionPrincipal principal,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orders.listMine(principal.id(), limit, beforeId));
    }

    @GetMapping(path = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> detail(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orders.detailMine(principal.id(), orderId));
    }

    @GetMapping(path = "/{orderId}/history",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OrderTimelineEntry>> history(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orders.historyMine(principal.id(), orderId));
    }
}
