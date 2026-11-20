package com.techstore.users.controller;

import com.techstore.users.service.EmailAlreadyRegisteredException;
import com.techstore.users.service.RegistrationValidationException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC Problem Details sin reflejar contenidos del registro, contraseñas ni
 * detalles SQL. Consejo limitado al controlador de registro.
 */
@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationExceptionHandler {

    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(RegistrationValidationException.class)
    public ResponseEntity<ProblemDetail> validation(
            RegistrationValidationException exception) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Los datos del registro no son válidos");
        body.setTitle("Solicitud inválida");
        body.setProperty("code", "VALIDATION_ERROR");
        List<Map<String, String>> fields = exception.errors().entrySet().stream()
                .map(entry -> Map.of(
                        "field", entry.getKey(),
                        "message", entry.getValue()))
                .toList();
        body.setProperty("errors", fields);
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> malformedJson(
            HttpMessageNotReadableException exception) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Se requiere un objeto JSON válido");
        body.setTitle("Solicitud inválida");
        body.setProperty("code", "VALIDATION_ERROR");
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> duplicate(
            EmailAlreadyRegisteredException exception) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        body.setTitle("Correo duplicado");
        body.setProperty("code", "EMAIL_ALREADY_REGISTERED");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON).body(body);
    }
}
