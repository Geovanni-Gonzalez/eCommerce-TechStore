package com.techstore.orders.payments;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta privada ADMIN de incidencias verificables; NUNCA es un endpoint
 * para forzar aprobaciones ni liberar UNKNOWN/STARTED sin prueba externa.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminPaymentIncidentsController {
    private final PaymentIncidentRepository incidents;

    public AdminPaymentIncidentsController(PaymentIncidentRepository incidents) {
        this.incidents = incidents;
    }

    @GetMapping(path="/{orderId}/payment-incidents",
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PaymentIncidentView>> list(
            @PathVariable long orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(incidents.findForOrder(orderId));
    }
}
