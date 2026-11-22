package com.techstore.inventory.admin;

import com.techstore.inventory.domain.InventoryBalance;
import java.util.List;

public record InventoryBalancePage(
        List<InventoryBalance> items, int page, int size,
        long totalElements, long totalPages
) {
    public InventoryBalancePage {
        items = List.copyOf(items);
    }
}
