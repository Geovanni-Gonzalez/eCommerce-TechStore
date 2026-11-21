package com.techstore.orders.payments;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** No divulgar PII ni señales internas a usuarios no autorizados. */
@RestControllerAdvice(assignableTypes=AdminPaymentIncidentsController.class)
public class PaymentIncidentExceptionHandler {
    @ExceptionHandler(PaymentReconciliationNotFoundException.class)
    public ResponseEntity<ProblemDetail> absent(Exception ignored) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "Pedido no encontrado");
        body.setProperty("code","PAYMENT_INCIDENT_ORDER_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .cacheControl(CacheControl.noStore()).body(body);
    }
}
