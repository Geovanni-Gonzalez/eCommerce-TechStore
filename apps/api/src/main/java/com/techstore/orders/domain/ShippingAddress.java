package com.techstore.orders.domain;

/** Dirección de entrega copiada en el pedido; no referencia perfiles mutables. */
public record ShippingAddress(
        String province,
        String canton,
        String district,
        String details,
        String recipientName,
        String phone
) {
    public ShippingAddress {
        province = field(province, "province", 1, 80);
        canton = field(canton, "canton", 1, 80);
        district = field(district, "district", 1, 80);
        details = field(details, "details", 5, 300);
        recipientName = field(recipientName, "recipientName", 2, 120);
        phone = field(phone, "phone", 8, 24);
    }

    private static String field(String text, String field, int min, int max) {
        if (text == null) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        String value = text.strip();
        if (value.length() < min || value.length() > max
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " no es válido");
        }
        return value;
    }
}
