package com.techstore.catalog.admin;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errores RFC 9457 sin revelar detalles SQL ni claves existentes. */
@RestControllerAdvice(assignableTypes=CatalogAdminController.class)
public final class CatalogAdminExceptionHandler {
    private static final MediaType PROBLEM = MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(CatalogAdminNotFound.class)
    ResponseEntity<ProblemDetail> missing() {
        return problem(HttpStatus.NOT_FOUND,"No encontrado",
                "El producto o categoría no existe","CATALOG_NOT_FOUND");
    }

    @ExceptionHandler({IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalid() {
        return problem(HttpStatus.BAD_REQUEST,"Datos inválidos",
                "Revisa los campos, identificadores y precios enviados","VALIDATION_ERROR");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict() {
        return problem(HttpStatus.CONFLICT,"Conflicto de catálogo",
                "SKU/nombre duplicado, categoría inexistente o categoría en uso",
                "CATALOG_CONFLICT");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,String title,String detail,String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status,detail);
        body.setTitle(title);
        body.setProperty("code",code);
        return ResponseEntity.status(status).contentType(PROBLEM).body(body);
    }
}
