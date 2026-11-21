package com.techstore.payments;

import com.techstore.orders.domain.CrcMoney;
import java.math.BigDecimal;

/**
 * Datos originados únicamente en backend: pedido, total CRC e intento único.
 * La clave no es un token de autenticación ni dato de tarjeta.
 */
public record PaymentAuthorizationRequest(
        long orderId, BigDecimal totalCrc, String idempotencyKey
) {
    public PaymentAuthorizationRequest {
        if (orderId < 1) {
            throw new IllegalArgumentException("Identificador de pedido inválido");
        }
        totalCrc = CrcMoney.require(totalCrc, 14, "totalCrc");
        if (totalCrc.signum() == 0) {
            throw new IllegalArgumentException("El monto del pago debe ser positivo");
        }
        if (idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9_-]{7,79}")) {
            throw new IllegalArgumentException("Identificador del intento inválido");
        }
    }
}
