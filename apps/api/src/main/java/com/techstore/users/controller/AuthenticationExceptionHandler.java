package com.techstore.users.controller;

import com.techstore.users.service.InvalidCredentialsException;
import com.techstore.users.service.LoginValidationException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Respuestas Problem Details sin credenciales, hashes ni detalles de BD. */
@RestControllerAdvice(assignableTypes = AuthenticationController.class)
public class AuthenticationExceptionHandler {

    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> invalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        problem.setTitle("Credenciales inválidas");
        problem.setProperty("code", "INVALID_CREDENTIALS");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(LoginValidationException.class)
    public ResponseEntity<ProblemDetail> invalidRequest(LoginValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Los datos de autenticación no son válidos");
        problem.setTitle("Solicitud inválida");
        problem.setProperty("code", "VALIDATION_ERROR");
        List<Map<String, String>> errors = ex.errors().entrySet().stream()
                .map(entry -> Map.of(
                        "field", entry.getKey(), "message", entry.getValue()))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> malformedJson(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Se requiere un objeto JSON válido");
        problem.setTitle("Solicitud inválida");
        problem.setProperty("code", "VALIDATION_ERROR");
        return ResponseEntity.badRequest().contentType(PROBLEM_JSON).body(problem);
    }
}
