package com.techstore.payments.webhook;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Nunca divulgar firma recibida, secreto, payload ni datos del usuario. */
@RestControllerAdvice(assignableTypes = SandboxWebhookController.class)
public class SandboxWebhookExceptionHandler {
    private static final MediaType PROBLEM =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(WebhookAuthenticationException.class)
    public ResponseEntity<ProblemDetail> unauthenticated(
            WebhookAuthenticationException ignored) {
        return problem(HttpStatus.UNAUTHORIZED, "WEBHOOK_AUTHENTICATION_FAILED",
                "No fue posible verificar la autenticidad del evento");
    }

    @ExceptionHandler({InvalidWebhookEventException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> badRequest(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_EVENT",
                "El evento del sandbox no es válido");
    }

    @ExceptionHandler(WebhookConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(WebhookConflictException ignored) {
        return problem(HttpStatus.CONFLICT, "WEBHOOK_EVENT_CONFLICT",
                "El evento o la referencia no coincide con un intento válido");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail) {
        ProblemDetail response = ProblemDetail.forStatusAndDetail(status, detail);
        response.setTitle("Evento de pago no aceptado");
        response.setProperty("code", code);
        return ResponseEntity.status(status).contentType(PROBLEM)
                .cacheControl(CacheControl.noStore()).body(response);
    }
}
