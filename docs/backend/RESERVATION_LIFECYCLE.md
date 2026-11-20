# BKL-027 — Liberación y vencimiento atómico de stock

## Alcance

Los pedidos creados por BKL-033 descuentan stock y crean reservas `RESERVED`. BKL-027 conecta la transición `PENDING -> CANCELLED` con la devolución idempotente del inventario, sin tocar pagos reales ni administración. El observador BKL-068 mantiene auditoría de creación: cancelación no elimina ese rastro.

## Contrato de cancelación

`POST /api/orders/{id}/cancel` — Bearer con rol CLIENT; el propietario proviene de SessionPrincipal, nunca del JSON.

| Caso | HTTP | Resultado |
|---|---:|---|
| PENDING del propietario | 204 | Estado CANCELLED + reservas RELEASED + stock devuelto |
| CANCELLED del propietario (reintento) | 204 | Ninguna segunda devolución |
| Pedido inexistente o de otro cliente | 404 | ORDER_NOT_FOUND; no revelar existencia ajena |
| Pedido CONFIRMED | 409 | ORDER_NOT_CANCELLABLE; no tocar stock |
| Sin token / rol ADMIN | 401 / 403 | Acceso rechazado |

Las respuestas 204 usan Cache-Control: no-store; errores son Problem Details. El carrito ya consumido por el checkout **no se repone automáticamente**.

## Secuencia transaccional

1. `OrderReservationService.cancelOwned` ejecuta `@Transactional` y bloquea la cabecera `orders` con `SELECT ... FOR UPDATE` filtrada por usuario.
2. Si el pedido es PENDING, `CheckoutStockRepository.releaseReservations` bloquea reservas RESERVED en orden de producto, cambia cada una a RELEASED y devuelve exactamente su cantidad al producto.
3. La misma transacción marca el pedido como CANCELLED. Una excepción revierte **estado del pedido, reserva y stock**.
4. Reintentar sobre CANCELLED no modifica la base. El mismo bloqueo serializa dos cancelaciones concurrentes.

Para pagos y estados futuros (BKL-040/BKL-045), adoptar el mismo orden de bloqueos: cabecera -> reservas -> productos; nunca cambiar PENDING a CONFIRMED después de una liberación.

## Expiración

Flyway V9 añade `stock_reservations.expires_at` con TTL de **30 minutos** desde `reserved_at` para reservas preexistentes y desde la creación de la nueva reserva para futuras. Solo las filas `RESERVED` vencidas son candidatas. `ReservationExpirationJob` consulta lotes de hasta 100 pedidos por ejecución, cada **60 segundos** (configurable), y llama a `expireOne`, cada pedido con **REQUIRES_NEW**. Antes de cancelar, se vuelve a bloquear y comprobar el pedido y su fecha; CONFIRMED o CANCELLED no se alteran. Si un pedido falla, se registra el error y se prosigue con el lote; una ejecución posterior puede reintentarlo.

Configuración:
- `CHECKOUT_RESERVATION_EXPIRY_ENABLED` (true por defecto; false para apagar el scheduler).
- `CHECKOUT_RESERVATION_EXPIRY_SCAN_MS` (60000 ms por defecto).
- El TTL actual es fijo en SQL: **30 minutos**. Para cambiarlo, crear una migración posterior, sin editar V9 en entornos existentes.

## Demostración SQL (base local de pruebas)

Crear un pedido de cliente con `POST /api/orders`; consultar:

~~~sql
SELECT o.id, o.status, r.status AS reservation_status,
       r.quantity, r.reserved_at, r.expires_at
FROM orders o
JOIN stock_reservations r ON r.order_id = o.id
ORDER BY o.id DESC LIMIT 10;
~~~

Simular un vencimiento **solo en una base de pruebas**:

~~~sql
UPDATE stock_reservations
SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
WHERE order_id = :order_id AND status = 'RESERVED';
~~~

Esperar al siguiente ciclo del scheduler (si está activado) o invocar el servicio en una prueba de integración. El pedido pasa a CANCELLED, reserva a RELEASED y el stock se restituye. **Nunca ejecutar el UPDATE simulado sobre datos reales.**

## Pruebas

- `ReservationLifecycleIT`: cancelación repetida, aislamiento por propietario, transacción revertida, dos cancelaciones simultáneas, expiración real y pedidos confirmados.
- `OrderCancellationHttpIT`: HTTP 401/403/404/409/204, ownership, seguridad Bearer y reintento.
- `CheckoutConcurrencyIT`: sigue garantizando que dos compradores no puedan vender la última unidad.
- `OrderObserverIT`: auditoría AFTER_COMMIT intacta.

Ejecutar `mvn -f apps/api/pom.xml clean verify` con PostgreSQL efímero `techstore_test` y validar el contrato OpenAPI.

## Límites

BKL-040 debe implementar confirmación de pedido + consumo de reserva con pago aprobado, y BKL-045 el resto de transiciones y el historial de cambios. Este flujo no emite evento de cancelación, no reembolsa pagos y no garantiza notificaciones externas. No es un motor completo de pedidos productivo.
