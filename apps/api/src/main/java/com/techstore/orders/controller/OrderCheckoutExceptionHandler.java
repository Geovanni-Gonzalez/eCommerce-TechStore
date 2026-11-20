package com.techstore.orders.controller;

import com.techstore.orders.dto.InvalidCheckoutRequestException;
import com.techstore.orders.inventory.InsufficientStockException;
import com.techstore.orders.pricing.EmptyCheckoutCartException;
import com.techstore.orders.pricing.UnavailableCheckoutItemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrderCheckoutController.class)
public final class OrderCheckoutExceptionHandler {
    private static final MediaType PROBLEM =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler({InvalidCheckoutRequestException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud debe incluir únicamente una dirección de entrega válida");
    }

    @ExceptionHandler(EmptyCheckoutCartException.class)
    public ResponseEntity<ProblemDetail> empty(EmptyCheckoutCartException ignored) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_CART",
                "El carrito no contiene productos");
    }

    @ExceptionHandler({UnavailableCheckoutItemException.class,
            InsufficientStockException.class})
    public ResponseEntity<ProblemDetail> stock(Exception ignored) {
        return problem(HttpStatus.CONFLICT, "CART_UNAVAILABLE",
                "Un artículo no está activo o no dispone del stock solicitado");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle("No se puede confirmar el pedido");
        body.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(PROBLEM).body(body);
    }
}
