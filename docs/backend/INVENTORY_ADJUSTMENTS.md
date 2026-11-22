# BKL-026 — Consultas y ajustes administrativos de inventario

Issue: https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/27

## Arquitectura y seguridad

BKL-025 crea `inventory_balances` e `inventory_movements` sobre las
reservas transaccionales del checkout. Este bloque ofrece consultas y
ajustes auditados de existencias, **exclusivos del rol ADMIN**. Las rutas
`/api/admin/**` ya están protegidas en `ApiSecurityConfig`; la identidad
del administrador proviene de `SessionPrincipal` verificado, no del body.

| Método | Ruta | Operación |
| --- | --- | --- |
| GET | `/api/admin/inventory?page=0&size=20` | Saldos paginados |
| GET | `/api/admin/inventory/products/{productId}` | Saldo de producto |
| GET | `/api/admin/inventory/products/{productId}/movements?page=0&size=20` | Historial auditado |
| POST | `/api/admin/inventory/products/{productId}/adjustments` | Entrada o salida auditable |

Las respuestas privadas usan `Cache-Control: no-store`. No hay
endpoint público de inventario ni acceso de CLIENT a ajustes.

## Request de ajuste (ejemplo ficticio)

```json
{
  "kind": "ADJUSTMENT_IN",
  "quantity": 15,
  "reason": "Recepción documentada de proveedor",
  "requestId": "19b286af-951a-4ee8-b613-fc0a1b2ee931"
}
```

Solo se aceptan las cuatro claves exactas; `kind` es
`ADJUSTMENT_IN` o `ADJUSTMENT_OUT`, `quantity` es entero de 1 a
1 000 000, `reason` tiene de 5 a 300 caracteres válidos y
`requestId` es UUID. No se aceptan actorId, stock absoluto, userId,
reservas o identidades enviadas desde el navegador.

El cliente debe generar un UUID **nuevo por cada decisión de ajuste** y
conservarlo para reintentar la misma solicitud si no recibió respuesta.
Al procesarse por primera vez, retorna **201** con
`{audit, balance, replayed:false}`; al reenviar idénticos parámetros
con el mismo UUID, retorna **200** y `replayed:true`, sin duplicar el
descuento/incremento. Reutilizar el UUID con valores distintos falla 409.

## Atomicidad y límites

- `InventoryAdminService.adjust` es `@Transactional`.
- Bloquea `products` con `SELECT ... FOR UPDATE` (mismo lock de
  checkout). Dos ajustes o una compra simultánea se serializan.
- Para salida exige `products.stock >= quantity`. Nunca consume
  reservas ajenas ni permite stock negativo.
- Para entrada evita overflow de `INTEGER` de PostgreSQL.
- Cambia `products.stock` y registra un `inventory_movements` de
  ajuste con delta físico/disponible, identidad ADMIN, motivo y UUID
  en la **misma transacción**. Si falla la auditoría, se revierte stock.
- Flyway V18 amplía únicamente la auditoría de V17 con
  `actor_user_id`, `reason`, `request_id` y un índice único; agrega
  constraints para los metadatos sin reescribir movimientos previos.
- Las reservas `RESERVED` siguen siendo propiedad del checkout:
  **físico = disponible + reservado**. Un ajuste entra/sale únicamente
  de disponible; no cambia reservas existentes.

## Errores

- 400: body, cantidad o paginación inválidos.
- 401/403: token ausente/inválido o rol insuficiente.
- 404: producto no existe.
- 409: stock insuficiente, overflow o requestId reutilizado con otra
  solicitud.
- 500: otro fallo interno (sin detalles SQL).
- Todos se representan como Problem Details sin PII.

## Próximo bloque y QA final

El panel visual de inventario/pedidos se entregará en **BKL-046**.
BKL-027 ya gestiona ciclo de reservas con checkout; no se altera.

Conforme a la decisión del equipo, quedan pendientes pruebas manuales
y de aceptación de esta funcionalidad para el final del producto:
roles ADMIN/CLIENT/anónimo, idempotencia concurrente, dos ajustes
simultáneos y ajuste concurrente con reserva, cantidades negativas o
overflow, unicidad de requestId y auditoría del ADMIN. La CI
preexistente no se desactiva.

La lectura de stock desde la vista `inventory_balances` no escribe
sobre tablas, y nunca reemplaza el mecanismo de reserva del pedido.
