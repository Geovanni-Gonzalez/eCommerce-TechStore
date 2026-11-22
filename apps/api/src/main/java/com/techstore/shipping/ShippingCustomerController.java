package com.techstore.shipping;

import com.techstore.users.security.SessionPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET para ROLE_CLIENT, filtrado por propietario también en SQL. */
@RestController
@RequestMapping("/api/orders")
public class ShippingCustomerController {
    private final ShippingCustomerService service;
    public ShippingCustomerController(ShippingCustomerService service) {
        this.service=service;
    }
    @GetMapping(value="/{orderId}/shipping",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShippingTrackingView> tracking(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.tracking(principal.id(),orderId));
    }
}
