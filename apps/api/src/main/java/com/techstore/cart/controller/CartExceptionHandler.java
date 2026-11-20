package com.techstore.cart.controller;

import com.techstore.cart.service.CartConflictException;
import com.techstore.cart.service.CartItemNotFoundException;
import com.techstore.cart.service.CartProductNotFoundException;
import com.techstore.cart.service.CartValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CartController.class)
public class CartExceptionHandler {

    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(CartValidationException.class)
    public ResponseEntity<ProblemDetail> invalid(CartValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                exception.getMessage(), "VALIDATION_ERROR");
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ProblemDetail> malformed(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "El JSON o identificador tiene un formato inválido", "VALIDATION_ERROR");
    }

    @ExceptionHandler({CartItemNotFoundException.class, CartProductNotFoundException.class})
    public ResponseEntity<ProblemDetail> notFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado",
                exception.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(CartConflictException.class)
    public ResponseEntity<ProblemDetail> stock(CartConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflicto de cantidad",
                exception.getMessage(), "CART_CONFLICT");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String title, String detail, String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setProperty("code", code);
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body);
    }
}
