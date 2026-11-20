package com.techstore.orders.pricing;

/** El precio se lee de nuevo, pero el artículo ya no está disponible (409). */
public final class UnavailableCheckoutItemException extends RuntimeException {
    public UnavailableCheckoutItemException() {
        super("El carrito contiene productos inactivos o sin stock suficiente");
    }
}
