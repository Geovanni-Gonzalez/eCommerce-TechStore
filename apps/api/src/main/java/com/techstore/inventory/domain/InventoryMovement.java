package com.techstore.inventory.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Registro histórico inmutable de cambios físicos, reservados y disponibles. */
public record InventoryMovement(
        long id,
        long productId,
        Long orderId,
        InventoryMovementKind kind,
        long quantity,
        long availableDelta,
        long reservedDelta,
        long physicalDelta,
        OffsetDateTime recordedAt
) {
    public InventoryMovement {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (id < 1 || productId < 1 || quantity < 1
                || physicalDelta != availableDelta + reservedDelta) {
            throw new IllegalArgumentException("Movimiento de inventario incoherente");
        }
        switch (kind) {
            case OPENING -> {
                if (orderId != null || physicalDelta != quantity
                        || availableDelta < 0 || reservedDelta < 0) {
                    throw new IllegalArgumentException("Apertura de inventario inválida");
                }
            }
            case RESERVED -> {
                if (orderId == null || orderId < 1 || physicalDelta != 0
                        || availableDelta != -quantity || reservedDelta != quantity) {
                    throw new IllegalArgumentException("Reserva de inventario inválida");
                }
            }
            case RELEASED -> {
                if (orderId == null || orderId < 1 || physicalDelta != 0
                        || availableDelta != quantity || reservedDelta != -quantity) {
                    throw new IllegalArgumentException("Liberación de inventario inválida");
                }
            }
            case CONSUMED -> {
                if (orderId == null || orderId < 1 || physicalDelta != -quantity
                        || availableDelta != 0 || reservedDelta != -quantity) {
                    throw new IllegalArgumentException("Consumo de inventario inválido");
                }
            }
            case ADJUSTMENT_IN -> {
                if (orderId != null || physicalDelta != quantity
                        || availableDelta != quantity || reservedDelta != 0) {
                    throw new IllegalArgumentException("Ajuste positivo inválido");
                }
            }
            case ADJUSTMENT_OUT -> {
                if (orderId != null || physicalDelta != -quantity
                        || availableDelta != -quantity || reservedDelta != 0) {
                    throw new IllegalArgumentException("Ajuste negativo inválido");
                }
            }
        }
    }
}
