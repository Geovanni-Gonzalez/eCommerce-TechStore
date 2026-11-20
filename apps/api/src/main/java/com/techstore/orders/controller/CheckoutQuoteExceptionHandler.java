package com.techstore.orders.controller;

import com.techstore.orders.pricing.EmptyCheckoutCartException;
import com.techstore.orders.pricing.UnavailableCheckoutItemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores estables sin exponer configuración o detalles de persistencia. */
@RestControllerAdvice(assignableTypes = CheckoutQuoteController.class)
public class CheckoutQuoteExceptionHandler {
    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(EmptyCheckoutCartException.class)
    public ResponseEntity<ProblemDetail> empty(EmptyCheckoutCartException ignored) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "El carrito está vacío", "EMPTY_CART");
    }

    @ExceptionHandler(UnavailableCheckoutItemException.class)
    public ResponseEntity<ProblemDetail> unavailable(
            UnavailableCheckoutItemException ignored) {
        return problem(HttpStatus.CONFLICT,
                "Hay artículos sin disponibilidad", "CART_UNAVAILABLE");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String description, String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, description);
        body.setTitle("No se puede cotizar");
        body.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(PROBLEM_JSON).body(body);
    }
}
