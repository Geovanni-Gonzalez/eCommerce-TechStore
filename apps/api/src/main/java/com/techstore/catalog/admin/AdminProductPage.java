package com.techstore.catalog.admin;

import java.util.List;

public record AdminProductPage(
        List<AdminProduct> items, int page, int size,
        long totalElements, long totalPages
) {
    public AdminProductPage {
        items = List.copyOf(items);
    }
}
