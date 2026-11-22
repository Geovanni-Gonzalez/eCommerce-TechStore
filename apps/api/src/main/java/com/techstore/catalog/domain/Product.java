package com.techstore.catalog.domain;

import java.math.BigDecimal;

/** Entidad catálogo; brand y categoryName se enriquecen con JOIN de lectura. */
public record Product(
        Long id, Long categoryId, String sku, String name, String description,
        BigDecimal priceCrc, int stock, boolean active,
        String brand, String categoryName
) {
    /** Compatibilidad con el constructor de dominio previo a BKL-021. */
    public Product(Long id, Long categoryId, String sku, String name,
            String description, BigDecimal priceCrc, int stock, boolean active) {
        this(id, categoryId, sku, name, description, priceCrc,
                stock, active, null, null);
    }
}
