package com.techstore.orders.controller;

import com.techstore.orders.query.InvalidOrderPageRequestException;
import com.techstore.orders.service.OrderNotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Sin información que distinga pedidos ajenos de pedidos inexistentes. */
@RestControllerAdvice(assignableTypes = {
        OrderReadController.class, AdminOrderReadController.class})
public class OrderReadExceptionHandler {
    private static final MediaType PROBLEM =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler({InvalidOrderPageRequestException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_ORDER_QUERY",
                "Parámetros inválidos: limit debe estar entre 1 y 100; beforeId positivo");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> missing(Exception ignored) {
        return problem(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                "El pedido solicitado no existe");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle("Consulta de pedido no disponible");
        body.setProperty("code", code);
        return ResponseEntity.status(status).contentType(PROBLEM)
                .cacheControl(CacheControl.noStore()).body(body);
    }
}
