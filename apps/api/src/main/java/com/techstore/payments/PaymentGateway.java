package com.techstore.payments;

/**
 * Puerto de autorización de pagos. La aplicación solo conoce este contrato;
 * ningún adaptador debe recibir PAN/CVV, datos de tarjeta o credenciales.
 *
 * BKL-036 persiste el intento idempotente antes de invocar este puerto.
 */
public interface PaymentGateway {
    PaymentAuthorization authorize(PaymentAuthorizationRequest request);
}
