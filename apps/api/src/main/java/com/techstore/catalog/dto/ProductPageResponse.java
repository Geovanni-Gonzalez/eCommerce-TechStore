package com.techstore.catalog.dto;

import java.util.List;

/**
 * Respuesta de paginación definida por el contrato OpenAPI de TechStore.
 */
public record ProductPageResponse(
        List<ProductResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages
) {
    public ProductPageResponse {
        items = List.copyOf(items);
    }
}
