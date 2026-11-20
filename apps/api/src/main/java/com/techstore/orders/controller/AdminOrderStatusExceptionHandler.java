package com.techstore.orders.controller;

import com.techstore.orders.service.OrderNotCancellableException;
import com.techstore.orders.service.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problem Details sin errores SQL ni datos del cliente. */
@RestControllerAdvice(assignableTypes = AdminOrderStatusController.class)
public class AdminOrderStatusExceptionHandler {
    private static final MediaType PROBLEM =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler({InvalidOrderStatusRequestException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATUS_REQUEST",
                "Se requiere únicamente un estado válido");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> missing(OrderNotFoundException ignored) {
        return problem(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                "El pedido solicitado no existe");
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<ProblemDetail> rejected(OrderNotCancellableException ignored) {
        return problem(HttpStatus.CONFLICT, "ORDER_TRANSITION_NOT_ALLOWED",
                "Esta transición no está habilitada para administración");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle("No se puede cambiar el estado del pedido");
        body.setProperty("code", code);
        return ResponseEntity.status(status).contentType(PROBLEM).body(body);
    }
}
