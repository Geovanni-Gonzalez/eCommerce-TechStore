package com.techstore.catalog.admin;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** BKL-023: JSON de escritura con allowlist estricta; no admite stock/userId. */
public final class CatalogAdminInput {
    private static final Set<String> PRODUCT_FIELDS = Set.of(
            "sku", "name", "description", "categoryId", "brand", "priceCrc");
    private CatalogAdminInput() { }

    public record ProductWrite(
            String sku, String name, String description, Long categoryId,
            String brand, BigDecimal priceCrc
    ) { }

    public static ProductWrite product(Map<String, Object> input) {
        keys(input, PRODUCT_FIELDS);
        String sku = requiredText(input.get("sku"), 2, 64, "SKU")
                .toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9][A-Z0-9_-]{1,63}")) {
            throw new IllegalArgumentException("SKU solo admite letras, números, - y _");
        }
        String name = requiredText(input.get("name"), 1, 160, "Nombre");
        String description = optionalText(input.get("description"), 3000, "Descripción");
        String brand = optionalText(input.get("brand"), 100, "Marca");
        Long categoryId = nullablePositiveId(input.get("categoryId"));
        Object rawPrice = input.get("priceCrc");
        if (!(rawPrice instanceof Number)) {
            throw new IllegalArgumentException("Precio CRC debe ser numérico");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(rawPrice.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Precio CRC no válido");
        }
        if (price.signum() < 0 || price.scale() > 2
                || price.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException(
                    "Precio CRC requiere 0 a 9999999999.99 con hasta dos decimales");
        }
        return new ProductWrite(sku, name, description, categoryId, brand, price);
    }

    public static String categoryName(Map<String, Object> input) {
        keys(input, Set.of("name"));
        return requiredText(input.get("name"), 1, 100, "Categoría");
    }

    public static boolean active(Map<String, Object> input) {
        keys(input, Set.of("active"));
        if (!(input.get("active") instanceof Boolean active)) {
            throw new IllegalArgumentException("active debe ser booleano");
        }
        return active;
    }

    private static void keys(Map<String, Object> body, Set<String> allowed) {
        if (body == null || !body.keySet().equals(allowed)) {
            throw new IllegalArgumentException(
                    "Campos faltantes o no permitidos en solicitud administrativa");
        }
    }

    private static String requiredText(Object raw, int min, int max, String field) {
        if (!(raw instanceof String text)) {
            throw new IllegalArgumentException(field + " debe ser texto");
        }
        String normalized = text.strip();
        if (normalized.length() < min || normalized.length() > max
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " tiene tamaño o caracteres no permitidos");
        }
        return normalized;
    }

    private static String optionalText(Object raw, int max, String field) {
        if (raw == null) return null;
        if (!(raw instanceof String text)) {
            throw new IllegalArgumentException(field + " debe ser texto o null");
        }
        String normalized = text.strip();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > max || normalized.codePoints()
                .anyMatch(c -> Character.isISOControl(c) && c != '\n')) {
            throw new IllegalArgumentException(field + " tiene caracteres no permitidos");
        }
        return normalized;
    }

    private static Long nullablePositiveId(Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof Number value)) {
            throw new IllegalArgumentException("categoryId debe ser entero positivo o null");
        }
        try {
            long id = new BigDecimal(value.toString()).longValueExact();
            if (id < 1) throw new ArithmeticException();
            return id;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("categoryId debe ser entero positivo");
        }
    }
}
