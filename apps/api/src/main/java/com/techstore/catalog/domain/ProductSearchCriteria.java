package com.techstore.catalog.domain;

import java.math.BigDecimal;

/** Filtros inmutables y opcionales validados por ProductService. */
public record ProductSearchCriteria(
        String query, Long categoryId, String brand,
        BigDecimal minPriceCrc, BigDecimal maxPriceCrc
) {
    public boolean isEmpty() {
        return query == null && categoryId == null && brand == null
                && minPriceCrc == null && maxPriceCrc == null;
    }
}
