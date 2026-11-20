package com.techstore.catalog.domain;

import java.math.BigDecimal;


public record Product (
        Long id,
        Long categoryId,
        String sku,
        String name,
        String description,
        BigDecimal priceCrc,
        int stock,
        boolean active
){
    
}
