package com.techstore.shipping;

/** Estados declarados por ADMIN, no comprobantes de un proveedor real. */
public enum ShippingStatus {
    PENDING_MANUAL, IN_TRANSIT, DELIVERED;
    public boolean canAdvanceTo(ShippingStatus target) {
        return (this==PENDING_MANUAL && target==IN_TRANSIT)
                || (this==IN_TRANSIT && target==DELIVERED);
    }
}
