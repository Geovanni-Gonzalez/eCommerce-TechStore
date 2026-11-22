package com.techstore.shipping;

import java.time.OffsetDateTime;

/** Sin actor, identidad, dirección ni notas libres del administrador. */
public record ShippingCustomerEvent(
        ShippingStatus status,String event,OffsetDateTime changedAt
) { }
