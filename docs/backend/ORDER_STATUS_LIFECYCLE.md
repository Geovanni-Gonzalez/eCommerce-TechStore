# BKL-045 — Transiciones de pedidos e historial auditable

## Objetivo

Integrar un historial de estado trazable con BKL-033 (creación), BKL-027 (cancelación de cliente y vencimiento), y una operación administrativa **segura** de cancelación. No se implementa la confirmación ante pago: eso requiere BKL-040.

## Tabla de transiciones

| Estado anterior | Acción | Estado nuevo | Quién | Inventario | Motivo |
|---|---|---|---|---|---|
| *(nuevo)* | Checkout confirmado en base | PENDING | CLIENT | Reserva descontada | CREATED |
| PENDING | Cancelación del propietario | CANCELLED | CLIENT | Devolver stock (una vez) | CLIENT_CANCEL |
| PENDING | Vencimiento de reserva | CANCELLED | SYSTEM | Devolver stock (una vez) | EXPIRED |
| PENDING | Cancelación administrativa | CANCELLED | ADMIN | Devolver stock (una vez) | ADMIN_CANCEL |
| CONFIRMED | Cualquier cancelación por esta API | — | ADMIN/CLIENT | No se altera | Rechazado |
| CANCELLED | Cancelación administrativa repetida | — | ADMIN | No se altera | 409 |
| CANCELLED | Cancelación CLIENT repetida | CANCELLED | CLIENT | Sin cambios | 204, sin nuevo evento |
| PENDING | Solicitar CONFIRMED sin pago acreditado | — | ADMIN | No se altera | 409 |

El estado PENDING es siempre un pedido **con reserva**, no equivale a cobro exitoso. El patrón GoF Observer de BKL-068 registra de modo independiente el evento de **creación** en V8; V10 es el historial transaccional de **estados**.

## Contratos HTTP

`PATCH /api/admin/orders/{id}/status` con Bearer **ADMIN**:

~~~json
{"status":"CANCELLED"}
~~~

Exige un JSON con **únicamente** `status`. Responde 204 si la transición PENDING->CANCELLED es válida, 400 por JSON o estado desconocido, 401 sin token, 403 sin ADMIN, 404 si el pedido no existe, 409 si la transición está prohibida, incluyendo solicitud CONFIRMED sin pago.

`GET /api/admin/orders/{id}/history`, Bearer ADMIN, entrega una lista de entradas con `id`, `orderId`, `fromStatus` (null en estado inicial), `toStatus`, `actorType`, `actorId` (null para SYSTEM), `reason`, `changedAt`. El resultado no incluye dirección ni credenciales. Ambos endpoints tienen `Cache-Control: no-store`.

## Persistencia Flyway V10

`order_status_history` contiene FK al pedido (sin borrado en cascada), tipos de estado y actor comprobados, actor identificado si es CLIENT/ADMIN, razón enumerada y tiempo del cambio. El historial se añade **dentro de la misma transacción** en que se crea/cancela pedido y se libera stock. Un rollback revierte también la anotación; bajo el candado de `orders FOR UPDATE`, dos cancelaciones no registran eventos ni devoluciones duplicados.

La migración V10 registra para los pedidos **ya existentes** una línea `BASELINE` con estado actual y actor SYSTEM; **no inventa** estados previos ni quién realizó el cambio. `BASELINE` es una fotografía de la situación al migrar, no un evento histórico comprobado. Los nuevos pedidos reciben `CREATED` en el propio checkout.

### Demostración SQL

~~~sql
SELECT h.order_id, h.from_status, h.to_status,
       h.actor_type, h.actor_id, h.reason, h.changed_at
FROM order_status_history h
WHERE h.order_id = :order_id
ORDER BY h.id;
~~~

Una creación seguida de cancelación válida produce las entradas `CREATED` y `ADMIN_CANCEL` / `CLIENT_CANCEL` / `EXPIRED`, según el origen, con la devolución de stock. Una transición rechazada o rollback no genera registro adicional.

## Validación

- `AdminOrderStatusIT`: autorización ADMIN/CLIENT, historial visible, transición válida, peticiones inválidas, CANCELLED repetida y no confirmación de pago.
- `ReservationLifecycleIT`: distingue CLIENT_CANCEL y EXPIRED, verifica rollback e idempotencia concurrente.
- `CheckoutFacadeTest`, `OrderObserverIT`, `CheckoutConcurrencyIT` y `CheckoutRollbackIT`: creación del historial y ausencia de entradas huérfanas.
- Maven `clean verify` sobre PostgreSQL efímero `techstore_test`, CI frontend/OpenAPI y smoke HTTP.

## Límites

Solo está disponible administrativamente PENDING->CANCELLED. **BKL-040** deberá introducir CONFIRMED después de verificar pago y consumir reservas, siguiendo orden de bloqueo cabecera -> reserva -> producto, y añadiendo otro motivo de transición mediante migración. BKL-046 añadirá interfaz administrativa. No hay reembolso ni envío; la tabla V10 no sustituye una bitácora externa a prueba de manipulación por administradores de base de datos.
