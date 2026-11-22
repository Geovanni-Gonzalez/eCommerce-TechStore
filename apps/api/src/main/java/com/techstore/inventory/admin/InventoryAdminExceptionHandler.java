package com.techstore.inventory.admin;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** RFC 9457 sin incluir SQL, stacktrace, email ni detalles de transacción. */
@RestControllerAdvice(assignableTypes=InventoryAdminController.class)
public class InventoryAdminExceptionHandler {
    private static final MediaType PROBLEM=MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(InventoryProductNotFound.class)
    ResponseEntity<ProblemDetail> missing() {
        return problem(HttpStatus.NOT_FOUND,"Producto no encontrado",
                "El producto no existe en inventario","INVENTORY_NOT_FOUND");
    }

    @ExceptionHandler({IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalid() {
        return problem(HttpStatus.BAD_REQUEST,"Solicitud inválida",
                "Revisa el tipo, cantidad, motivo, requestId y paginación",
                "INVENTORY_VALIDATION_ERROR");
    }

    @ExceptionHandler({InventoryAdjustmentConflict.class,
            DataIntegrityViolationException.class})
    ResponseEntity<ProblemDetail> conflict() {
        return problem(HttpStatus.CONFLICT,"Conflicto de inventario",
                "El saldo es insuficiente, se alcanzó el límite o la solicitud ya se utilizó",
                "INVENTORY_CONFLICT");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status,
            String title,String detail,String code) {
        ProblemDetail body=ProblemDetail.forStatusAndDetail(status,detail);
        body.setTitle(title);
        body.setProperty("code",code);
        return ResponseEntity.status(status).contentType(PROBLEM).body(body);
    }
}
