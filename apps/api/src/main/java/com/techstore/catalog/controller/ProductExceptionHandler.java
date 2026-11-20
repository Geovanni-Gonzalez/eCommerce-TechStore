package com.techstore.catalog.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce errores del catálogo a RFC Problem Details; no filtra detalles
 * de infraestructura ni afecta a otros módulos del monolito.
 */
@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductExceptionHandler {

    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> productNotFound(
            ProductNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Producto no encontrado",
                exception.getMessage(), "PRODUCT_NOT_FOUND");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> invalidInput(
            IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                exception.getMessage(), "VALIDATION_ERROR");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> invalidParameterType(
            MethodArgumentTypeMismatchException exception) {
        String detail = "El parámetro '" + exception.getName()
                + "' debe ser un número entero válido";
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                detail, "VALIDATION_ERROR");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String title, String detail, String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(PROBLEM_JSON)
                .body(body);
    }
}
