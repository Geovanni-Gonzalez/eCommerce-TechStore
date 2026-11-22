package com.techstore.shipping;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Auditoría ADMIN: sin PII ni guía de transportista inventada. */
public record ShipmentHistoryEntry(
        long id,long shipmentId,ShippingStatus fromStatus,ShippingStatus toStatus,
        long actorUserId,String reason,OffsetDateTime changedAt
) {
    public ShipmentHistoryEntry {
        if(id<1||shipmentId<1||actorUserId<1)throw new IllegalArgumentException("ID inválido");
        Objects.requireNonNull(toStatus);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(changedAt);
    }
}
