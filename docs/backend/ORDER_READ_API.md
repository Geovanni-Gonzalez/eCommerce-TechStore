# BKL-042 — API de historial y detalle de pedidos con propiedad del usuario

## Alcance

Las consultas de pedidos son **solo lectura** y utilizan los snapshots comerciales existentes de PostgreSQL (Flyway V6). No recalculan precios desde el catálogo, no autorizan cobros, ni cambian inventario. El campo `paymentMode=SIMULATED` representa simulación y no acredita un pago bancario.

## Endpoints

| Ruta | Permiso | Respuesta |
| --- | --- | --- |
| `GET /api/orders?limit=20&beforeId=...` | ROLE_CLIENT | `OrderPage<OrderSummary>` |
| `GET /api/orders/{id}` | ROLE_CLIENT y propietario | `OrderResponse` |
| `GET /api/orders/{id}/history` | ROLE_CLIENT y propietario | `OrderTimelineEntry[]` |
| `GET /api/admin/orders?limit=20&beforeId=...` | ROLE_ADMIN | `OrderPage<AdminOrderSummary>` |
| `GET /api/admin/orders/{id}` | ROLE_ADMIN | `AdminOrderDetail` |
| `GET /api/admin/orders/{id}/history` | ROLE_ADMIN | Historial auditable completo (BKL-045) |

Cada ruta exige token Bearer revocable de BKL-016. La autorización distingue explícitamente ROLE_CLIENT y ROLE_ADMIN en Spring Security. No se acepta `userId` del navegador para determinar el propietario: `SessionPrincipal.id()` es la única fuente y el repositorio filtra `WHERE user_id=?` en **PostgreSQL**. El detalle consulta `WHERE id=? AND user_id=?` antes de recuperar líneas. El historial verifica propiedad antes de leer registros.

**Privacidad / IDOR:** un pedido ajeno y uno inexistente devuelven **404 ORDER_NOT_FOUND** indistinguible. CLIENT no puede usar `/api/admin/**` ni ADMIN puede usar rutas exclusivas de CLIENT. Las respuestas 200, 400 y 404 incluyen `Cache-Control: no-store` (401/403 también lo incluyen en la cadena de seguridad). No se exponen tokens, hashes ni referencias de la pasarela.

## Contratos de salida

- **OrderSummary:** `id`, `status`, `paymentMode`, `totals`, `createdAt`. El listado no selecciona direcciones, teléfonos, nombre de destinatario ni IDs de otros usuarios.
- **OrderResponse:** detalle del propietario con líneas históricas, cantidades, precio `CRC`, totales y dirección histórica del propio pedido.
- **OrderTimelineEntry:** `fromStatus`, `toStatus`, `actorType`, `reason`, `changedAt`. Se omiten `actorId`, IDs globales del historial y `orderId` redundante; el endpoint ADMIN original mantiene la auditoría interna.
- **AdminOrderSummary / AdminOrderDetail:** incluyen `ownerUserId` solo bajo `/api/admin/orders`.

Un cambio posterior en `products.name` o `products.price_crc` **no modifica** el detalle histórico. `CONFIRMED` en el sistema actual solo puede corresponder a simulación BKL-040, habilitada explícitamente; no equivale a captura bancaria.

## Paginación por cursor

`limit` admite valores de **1 a 100** (default **20**). `beforeId` es opcional, entero positivo. El orden es `id DESC` y el cursor es exclusivo: `id < beforeId`. Se recuperan hasta `limit+1` filas para decidir `hasMore` sin un COUNT adicional.

Ejemplo de primera página:
```json
{"items":[{"id":202,"status":"PENDING","paymentMode":"SIMULATED","totals":{"subtotalCrc":1000,"shippingCrc":2500,"taxCrc":130,"totalCrc":3630},"createdAt":"2026-11-21T12:00:00Z"}],"hasMore":true,"nextBeforeId":202}
```
La siguiente solicitud usa `?limit=20&beforeId=202`. Una página terminal tiene `hasMore=false` y `nextBeforeId=null`. El cursor se mantiene estable frente a nuevas inserciones, pero no representa una instantánea global ante transacciones concurrentes.

Los parámetros inválidos devuelven **400 INVALID_ORDER_QUERY** sin mensajes SQL. Las consultas SQL parametrizan los filtros; no existe interpolación de identificadores de usuario o cursores recibidos por HTTP.

## Validación y relación con el frontend

`OrderQueryHttpIT` prueba con PostgreSQL `techstore_test` + MockMvc/Spring Security: anonimato (401), roles cruzados (403), pedidos ajenos (404), intentos de suplantación por querystring, respuestas sin PII, snapshots históricos, límites de página, cursor, cambios de estado y comparación de historial CLIENT/ADMIN. Se conserva la auditoría BKL-045 y la cancelación BKL-027/BKL-041.

OpenAPI declara **31 operaciones**, sin rutas previstas pendientes. **BKL-043** podrá usar el listado, detalle y línea temporal; **BKL-039** no debe deducir aprobación por una URL de retorno y debe consultar estado confiable.
