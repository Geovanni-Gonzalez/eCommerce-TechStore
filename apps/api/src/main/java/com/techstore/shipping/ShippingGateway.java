package com.techstore.shipping;

/**
 * Puerto logístico (no confundir con ShippingStrategy de tarifas).
 * BKL-047 ofrece un adaptador MANUAL sin redes ni llamadas a transportistas.
 * La capa de servicio verifica CONFIRMED y bloquea orders FOR UPDATE antes
 * de invocar este puerto. Ningún adaptador puede confirmar un pago.
 */
public interface ShippingGateway {
    ShippingRegistration register(ShippingRegistrationCommand command);
}
