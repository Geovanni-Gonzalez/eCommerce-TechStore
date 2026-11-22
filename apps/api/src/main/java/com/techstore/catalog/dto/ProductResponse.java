package com.techstore.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.techstore.catalog.domain.Product;
import java.math.BigDecimal;
import java.util.Objects;

/** Contrato público; nunca expone productos inactivos ni datos internos. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductResponse(
        Long id, String sku, String name, String description,
        CategoryResponse category, BigDecimal priceCrc, int stock,
        String imageUrl, boolean active, String brand
) {
    /** Compatibilidad con la firma de ocho campos de BKL-018. */
    public ProductResponse(Long id, String sku, String name, String description,
            CategoryResponse category, BigDecimal priceCrc, int stock,
            String imageUrl, boolean active) {
        this(id, sku, name, description, category, priceCrc, stock,
                imageUrl, active, null);
    }
    public static ProductResponse from(Product product) {
        Objects.requireNonNull(product, "product");
        CategoryResponse category = product.categoryId() != null
                && product.categoryName() != null
                ? new CategoryResponse(product.categoryId(), product.categoryName())
                : null;
        return new ProductResponse(
                product.id(), product.sku(), product.name(),
                product.description(), category, product.priceCrc(),
                product.stock(), null, product.active(), product.brand());
    }
}
