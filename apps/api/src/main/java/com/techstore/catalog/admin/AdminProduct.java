package com.techstore.catalog.admin;

import java.math.BigDecimal;

/** Vista administrativa: stock informativo, nunca editable desde este CRUD. */
public record AdminProduct(
        long id, String sku, String name, String description,
        Long categoryId, String categoryName, String brand,
        BigDecimal priceCrc, int stock, boolean active
) { }
