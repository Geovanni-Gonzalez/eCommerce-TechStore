package com.techstore.inventory.admin;

import com.techstore.inventory.domain.InventoryMovementKind;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Payload estricto: evita escrituras arbitrarias a stock, saldo o auditoría. */
public record InventoryAdjustmentCommand(
        InventoryMovementKind kind, int quantity, String reason, UUID requestId
) {
    private static final Set<String> KEYS =
            Set.of("kind", "quantity", "reason", "requestId");
    private static final int MAX_QUANTITY = 1_000_000;

    public InventoryAdjustmentCommand {
        if (kind != InventoryMovementKind.ADJUSTMENT_IN
                && kind != InventoryMovementKind.ADJUSTMENT_OUT) {
            throw new IllegalArgumentException("Solo se permiten ajustes de entrada o salida");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Cantidad fuera del rango 1..1000000");
        }
        if (reason == null || reason.length() < 5 || reason.length() > 300
                || !reason.equals(reason.strip())
                || reason.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Motivo de ajuste inválido");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("Falta ID de idempotencia");
        }
    }

    public static InventoryAdjustmentCommand from(Map<String, Object> body) {
        if (body == null || !body.keySet().equals(KEYS)) {
            throw new IllegalArgumentException("Campos obligatorios o no permitidos");
        }
        if (!(body.get("kind") instanceof String kindText)
                || !(kindText.equals("ADJUSTMENT_IN")
                    || kindText.equals("ADJUSTMENT_OUT"))) {
            throw new IllegalArgumentException("kind debe ser ADJUSTMENT_IN o ADJUSTMENT_OUT");
        }
        if (!(body.get("quantity") instanceof Number rawQuantity)) {
            throw new IllegalArgumentException("quantity debe ser entero");
        }
        int quantity;
        try {
            quantity = new BigDecimal(rawQuantity.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("quantity debe ser entero", ex);
        }
        if (!(body.get("reason") instanceof String rawReason)) {
            throw new IllegalArgumentException("reason debe ser texto");
        }
        String reason = rawReason.strip();
        // Rechazar cadenas con whitespace de control, incluso cuando son externas.
        if (!(body.get("requestId") instanceof String key)
                || !key.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("requestId debe ser UUID canónico");
        }
        return new InventoryAdjustmentCommand(
                InventoryMovementKind.valueOf(kindText),
                quantity, reason, UUID.fromString(key));
    }
}
