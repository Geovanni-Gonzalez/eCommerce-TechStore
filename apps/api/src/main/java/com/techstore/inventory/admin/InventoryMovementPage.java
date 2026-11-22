package com.techstore.inventory.admin;

import java.util.List;

public record InventoryMovementPage(
        long productId, List<InventoryMovementAudit> items,
        int page, int size, long totalElements, long totalPages
) {
    public InventoryMovementPage {
        items = List.copyOf(items);
    }
}
