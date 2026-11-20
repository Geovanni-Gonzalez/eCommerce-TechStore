package com.techstore.orders.dto;

public final class InvalidCheckoutRequestException extends IllegalArgumentException {
    public InvalidCheckoutRequestException() {
        super("La dirección de entrega es obligatoria y debe contener solo sus campos válidos");
    }
}
