/**
 * Pedidos históricos BKL-030, cotización BKL-032 y checkout BKL-033.
 *
 * GoF Strategy intercambia políticas de envío; GoF Facade coordina las
 * operaciones transaccionales. BKL-068 añadirá un Observer concreto sobre
 * OrderCreatedEvent cuando el pedido se confirme en base de datos.
 */
package com.techstore.orders;
