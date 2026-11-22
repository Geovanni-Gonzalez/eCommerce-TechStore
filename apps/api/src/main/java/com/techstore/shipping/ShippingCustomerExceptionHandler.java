package com.techstore.shipping;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** No distingue pedidos ajenos de inexistentes y no filtra PII. */
@RestControllerAdvice(assignableTypes=ShippingCustomerController.class)
public class ShippingCustomerExceptionHandler {
    private static final MediaType PROBLEM=MediaType.parseMediaType("application/problem+json");
    @ExceptionHandler({ShippingNotFoundException.class,IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> notFound() {
        ProblemDetail pd=ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "El pedido no existe o no pertenece a esta cuenta");
        pd.setTitle("Seguimiento no disponible");
        pd.setProperty("code","SHIPPING_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(PROBLEM)
                .cacheControl(CacheControl.noStore()).body(pd);
    }
}
