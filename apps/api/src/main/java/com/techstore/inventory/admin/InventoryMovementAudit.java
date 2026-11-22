package com.techstore.inventory.admin;

import com.techstore.inventory.domain.InventoryMovement;
import java.util.UUID;

/** Trazabilidad de movimientos para ADMIN, sin emails ni credenciales. */
public record InventoryMovementAudit(
        InventoryMovement movement, Long actorUserId, String reason, UUID requestId
) { }
