package com.techstore.orders.controller;

import com.techstore.orders.service.OrderNotCancellableException;
import com.techstore.orders.service.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** El mismo 404 para pedido inexistente y ajeno: no filtrar información. */
@RestControllerAdvice(assignableTypes=OrderCancellationController.class)
public class OrderCancellationExceptionHandler {
    private static final MediaType PROBLEM =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(OrderNotFoundException ignored) {
        return problem(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                "No existe el pedido solicitado");
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<ProblemDetail> conflict(OrderNotCancellableException ignored) {
        return problem(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE",
                "El pedido no está pendiente");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle("No se puede cancelar el pedido");
        body.setProperty("code", code);
        return ResponseEntity.status(status).contentType(PROBLEM).body(body);
    }
}
