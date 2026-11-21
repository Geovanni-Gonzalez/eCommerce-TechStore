package com.techstore.payments;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** No revelar si existe un pedido ajeno ni registrar la clave enviada. */
@RestControllerAdvice(assignableTypes=PaymentAttemptController.class)
public class PaymentAttemptExceptionHandler {
    private static final MediaType PROBLEM =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler({InvalidPaymentAttemptRequestException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception ignored) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_ATTEMPT",
                "Se requiere Idempotency-Key válido y ningún body");
    }

    @ExceptionHandler(PaymentAttemptNotFoundException.class)
    public ResponseEntity<ProblemDetail> missing(PaymentAttemptNotFoundException ignored) {
        return response(HttpStatus.NOT_FOUND, "PAYMENT_ATTEMPT_NOT_FOUND",
                "No existe el pedido o intento solicitado");
    }

    @ExceptionHandler(PaymentAttemptConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(PaymentAttemptConflictException ignored) {
        return response(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_CONFLICT",
                "Clave reutilizada o pedido con intento activo, aprobado o incierto");
    }

    private static ResponseEntity<ProblemDetail> response(
            HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle("No se pudo registrar el intento de pago");
        body.setProperty("code", code);
        return ResponseEntity.status(status).contentType(PROBLEM)
                .cacheControl(CacheControl.noStore()).body(body);
    }
}
