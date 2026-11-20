package com.techstore.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.techstore.catalog.domain.Product;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Contrato público del catálogo: no se expone directamente el modelo de dominio.
 * Los campos opcionales no disponibles en la migración V1 se omiten del JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        CategoryResponse category,
        BigDecimal priceCrc,
        int stock,
        String imageUrl,
        boolean active
) {
    public static ProductResponse from(Product product) {
        Objects.requireNonNull(product, "product");

        // V1 tiene category_id, pero no el nombre de categoría en el modelo;
        // no inventamos el nombre ni devolvemos un objeto Category incompleto.
        // Tampoco existe image_url en la tabla V1.
        return new ProductResponse(
                product.id(),
                product.sku(),
                product.name(),
                product.description(),
                null,
                product.priceCrc(),
                product.stock(),
                null,
                product.active()
        );
    }
}
