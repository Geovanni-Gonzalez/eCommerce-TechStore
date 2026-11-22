package com.techstore.shipping;

import com.techstore.users.security.SessionPrincipal;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo /api/admin/**, protegido por ROLE_ADMIN (ApiSecurityConfig).
 * No hay endpoint público para fingir tracking o cambiar estado de pago.
 */
@RestController
@RequestMapping("/api/admin/shipments")
public class ShippingAdminController {
    private final ShippingAdminService service;

    public ShippingAdminController(ShippingAdminService service) {
        this.service = service;
    }

    @GetMapping(value="/orders/{orderId}",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShipmentRecord> find(@PathVariable long orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.findByOrderId(orderId));
    }

    @PostMapping(value="/orders/{orderId}",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShipmentRecord> register(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long orderId,
            @RequestBody(required=false) Object body) {
        if(body!=null) {
            throw new IllegalArgumentException("No se permite cuerpo en registro manual");
        }
        ShippingRegistration result=service.register(principal.id(),orderId);
        ResponseEntity.BodyBuilder response=result.created()
                ?ResponseEntity.created(URI.create(
                    "/api/admin/shipments/orders/"+orderId))
                :ResponseEntity.ok();
        return response.cacheControl(CacheControl.noStore()).body(result.shipment());
    }
}
