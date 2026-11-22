package com.techstore.reviews;

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

/** No revelar SQL, propietario, direcciones ni IDs de pedidos. */
@RestControllerAdvice(assignableTypes=ProductReviewController.class)
public class ProductReviewExceptionHandler {
    private static final MediaType PROBLEM =
            MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler({InvalidReviewRequestException.class,
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> invalid() {
        return problem(HttpStatus.BAD_REQUEST,"INVALID_REVIEW_REQUEST",
                "Calificación 1–5 y comentario de 5–1000 caracteres sin controles");
    }

    @ExceptionHandler(ReviewProductNotFoundException.class)
    ResponseEntity<ProblemDetail> missing() {
        return problem(HttpStatus.NOT_FOUND,"REVIEW_PRODUCT_NOT_FOUND",
                "El producto no existe");
    }

    @ExceptionHandler({ReviewNotEligibleException.class,
            DataIntegrityViolationException.class})
    ResponseEntity<ProblemDetail> conflict() {
        return problem(HttpStatus.CONFLICT,"REVIEW_NOT_ELIGIBLE",
                "Se requiere compra entregada manualmente y ninguna reseña activa");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,String code,String detail) {
        ProblemDetail pd=ProblemDetail.forStatusAndDetail(status,detail);
        pd.setTitle("Reseña no disponible");
        pd.setProperty("code",code);
        return ResponseEntity.status(status).contentType(PROBLEM)
                .cacheControl(CacheControl.noStore()).body(pd);
    }
}
