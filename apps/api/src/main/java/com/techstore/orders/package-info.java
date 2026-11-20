/**
 * Pedidos históricos BKL-030, cotización BKL-032 y checkout BKL-033.
 *
 * GoF Strategy intercambia políticas de envío; GoF Facade coordina las
 * operaciones transaccionales. BKL-068 implementa Observer concreto AFTER_COMMIT con evidencia de
 * auditoría idempotente; no equivale a una entrega durable con outbox.
 */
package com.techstore.orders;
