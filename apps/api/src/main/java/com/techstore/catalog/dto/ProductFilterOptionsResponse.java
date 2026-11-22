package com.techstore.catalog.dto;

import java.util.List;

/** Facetas disponibles en catálogo activo; sin exponer datos de admin. */
public record ProductFilterOptionsResponse(
        List<CategoryResponse> categories, List<String> brands
) {
    public ProductFilterOptionsResponse {
        categories = List.copyOf(categories);
        brands = List.copyOf(brands);
    }
}
