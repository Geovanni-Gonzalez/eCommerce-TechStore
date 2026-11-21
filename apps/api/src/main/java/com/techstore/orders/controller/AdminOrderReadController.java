package com.techstore.orders.controller;

import com.techstore.orders.query.AdminOrderDetail;
import com.techstore.orders.query.AdminOrderSummary;
import com.techstore.orders.query.OrderPage;
import com.techstore.orders.query.OrderQueryService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Solo ROLE_ADMIN por cadena de seguridad; no habilitar en /api/orders. */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderReadController {
    private final OrderQueryService orders;

    public AdminOrderReadController(OrderQueryService orders) {
        this.orders = orders;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderPage<AdminOrderSummary>> list(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orders.listAdmin(limit, beforeId));
    }

    @GetMapping(path = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminOrderDetail> detail(@PathVariable long orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orders.detailAdmin(orderId));
    }
}
