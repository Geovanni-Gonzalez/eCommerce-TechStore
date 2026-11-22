package com.techstore.inventory.admin;

import com.techstore.inventory.domain.InventoryBalance;

/** replayed=true: petición idempotente existente, no vuelve a tocar stock. */
public record InventoryAdjustmentResult(
        InventoryMovementAudit audit, InventoryBalance balance, boolean replayed
) { }
