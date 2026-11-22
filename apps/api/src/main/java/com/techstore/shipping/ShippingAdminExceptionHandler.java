package com.techstore.shipping;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Problem Details sin SQL, datos personales ni secretos de transporte. */
@RestControllerAdvice(assignableTypes=ShippingAdminController.class)
public class ShippingAdminExceptionHandler {
    private static final MediaType PROBLEM=
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler({IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalid() {
        return problem(HttpStatus.BAD_REQUEST,"INVALID_SHIPMENT_REQUEST",
                "El identificador del pedido debe ser positivo");
    }

    @ExceptionHandler(ShippingNotFoundException.class)
    ResponseEntity<ProblemDetail> missing() {
        return problem(HttpStatus.NOT_FOUND,"SHIPMENT_NOT_FOUND",
                "No se encontró el pedido o su registro logístico");
    }

    @ExceptionHandler({ShippingOrderNotConfirmedException.class,
            DataIntegrityViolationException.class})
    ResponseEntity<ProblemDetail> conflict() {
        return problem(HttpStatus.CONFLICT,"SHIPMENT_NOT_ALLOWED",
                "Solo un pedido confirmado admite registro logístico manual");
    }

    @ExceptionHandler(ShippingGatewayUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE,"SHIPPING_DISABLED",
                "El registro logístico está deshabilitado");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,String code,String detail) {
        ProblemDetail value=ProblemDetail.forStatusAndDetail(status,detail);
        value.setTitle("Gestión logística no disponible");
        value.setProperty("code",code);
        return ResponseEntity.status(status).contentType(PROBLEM)
                .cacheControl(CacheControl.noStore()).body(value);
    }
}
