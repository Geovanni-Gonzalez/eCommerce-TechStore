package com.techstore.payments.sessions;

import com.techstore.payments.InvalidPaymentAttemptRequestException;
import com.techstore.payments.PaymentAttemptConflictException;
import com.techstore.payments.PaymentAttemptNotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** No divulgar tokens o claves idempotentes en errores. */
@RestControllerAdvice(assignableTypes=SandboxCheckoutSessionController.class)
public class SandboxCheckoutSessionExceptionHandler {
    private static final MediaType PROBLEM =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler({SandboxSessionInvalidRequestException.class,
            InvalidPaymentAttemptRequestException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_SANDBOX_SESSION_REQUEST",
                "Solicitud inválida: token, clave o body no permitido");
    }

    @ExceptionHandler({SandboxSessionUnavailableException.class,
            PaymentAttemptNotFoundException.class})
    public ResponseEntity<ProblemDetail> unavailable(Exception ignored) {
        return problem(HttpStatus.NOT_FOUND, "SANDBOX_SESSION_NOT_FOUND",
                "Sesión o pedido no disponible");
    }

    @ExceptionHandler({SandboxSessionConflictException.class,
            PaymentAttemptConflictException.class})
    public ResponseEntity<ProblemDetail> conflict(Exception ignored) {
        return problem(HttpStatus.CONFLICT, "SANDBOX_SESSION_CONFLICT",
                "El intento ya fue utilizado o el pedido no admite un pago nuevo");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle("No se pudo iniciar o continuar el sandbox");
        body.setProperty("code", code);
        return ResponseEntity.status(status).contentType(PROBLEM)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(body);
    }
}
