package com.techstore.orders.payments;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores sin información de la firma ni del cliente; HTTP ADMIN. */
@RestControllerAdvice(assignableTypes=AdminPaymentReconciliationController.class)
public class PaymentReconciliationExceptionHandler {
    private static final MediaType PROBLEM =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler({InvalidPaymentReconciliationRequestException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_RECONCILIATION_REQUEST",
                "La conciliación no admite body ni parámetros de pago");
    }

    @ExceptionHandler(PaymentReconciliationNotFoundException.class)
    public ResponseEntity<ProblemDetail> absent(Exception ignored) {
        return problem(HttpStatus.NOT_FOUND, "PAYMENT_EVENT_NOT_FOUND",
                "Evento verificado inexistente");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String message) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, message);
        body.setTitle("Conciliación de sandbox no disponible");
        body.setProperty("code", code);
        return ResponseEntity.status(status).contentType(PROBLEM)
                .cacheControl(CacheControl.noStore()).body(body);
    }
}
