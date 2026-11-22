# BKL-047 — Contrato ShippingGateway y alternativa manual segura

## Decisión arquitectónica: manual primero

Este bloque cumple la necesidad de BKL-006 y BKL-047 de definir un contrato
intercambiable **sin elegir un transportista ni fingir su integración**.
En el prototipo no existe contrato ni credenciales de proveedor logístico.
La alternativa inicial es **registro de gestión manual supervisado por ADMIN**.
La API no consulta ninguna red, no compra etiquetas, no envía correo ni publica
números de guía, ETA, transportistas o estados ficticios.

Esto es distinto de `ShippingStrategy` (GoF Strategy) usado por el checkout
para el **costo de entrega**. `ShippingGateway` se encarga únicamente de
la **gestión logística posterior a la confirmación de pago**, y el adaptador
no modifica el precio de envío cobrado previamente.

## Arquitectura y dependencias

- `ShippingGateway`: interfaz/puerto `register(ShippingRegistrationCommand)`.
- `DisabledShippingGateway`: falla cerrado sin escribir la BD.
- `ManualShippingGateway`: crea, por medio del adaptador JDBC, una tarea
  de preparación con estado fijo `PENDING_MANUAL`.
- `ShippingGatewayConfig`: proveedor `DISABLED` por defecto; cambiar a
  `MANUAL` requiere `TECHSTORE_SHIPPING_PROVIDER=MANUAL`. Cualquier otro
  valor falla al iniciar la aplicación. No existen adaptadores reales.
- `ShippingAdminService`: revisa pedido `CONFIRMED` con `SELECT ... FOR UPDATE`
  y registra la tarea dentro de `@Transactional`, sin cambiar pedido, pago,
  reserva o movimiento de inventario.
- `JdbcShipmentRepository`: SQL parametrizado, `UNIQUE(order_id)` y
  `ON CONFLICT DO NOTHING` para idempotencia por pedido, incluso en
  solicitudes administrativas repetidas.
- Flyway `V20__manual_shipment_registrations.sql`: tabla `shipments`
  con orden única, estado `PENDING_MANUAL`, creador ADMIN y fecha UTC.
  Las restricciones y un trigger impiden registrar pedidos no confirmados.
  Otro trigger prohíbe UPDATE y DELETE por ahora, hasta tener cambios de
  estado auditables en BKL-048. La dirección y el teléfono **no se duplican**:
  permanecen en el snapshot histórico de `orders`.

## Endpoints protegidos

Todos requieren `Authorization: Bearer <token>` ROLE_ADMIN; los CLIENT y
visitantes no pueden crear ni leer la tarea. Las respuestas contienen
`Cache-Control: no-store` y los errores siguen Problem Details sin SQL/PII.

| HTTP | Ruta | Resultado |
| --- | --- | --- |
| `POST` | `/api/admin/shipments/orders/{orderId}` | Crear tarea para CONFIRMED; 201 nueva, 200 existente |
| `GET` | `/api/admin/shipments/orders/{orderId}` | Consultar registro; 200 o 404 |

El POST **no recibe body**, precios, dirección, IDs de usuario, estado
deseado o transportista. La identidad de quien registra proviene de
`SessionPrincipal`. El servicio verifica el pedido y el rol antes de
invocar el gateway. La misma orden no admite más de una tarea.

Ejemplo con un administrador autenticado y pedido de ID ficticio 123:

```http
POST /api/admin/shipments/orders/123
Authorization: Bearer <token-admin>
```

Ejemplo de estado de respuesta (nunca despacho real):

```json
{
  "id": 1,
  "orderId": 123,
  "fulfillmentMethod": "MANUAL",
  "status": "PENDING_MANUAL",
  "openedByUserId": 8,
  "registeredAt": "2026-11-21T18:00:00Z"
}
```

| HTTP | Significado |
| --- | --- |
| 200 | Consulta existente o POST repetido (idempotente) |
| 201 | Primera tarea registrada |
| 400 | ID del pedido inválido |
| 401/403 | Falta sesión o no tiene rol ADMIN |
| 404 | Pedido inexistente o tarea no registrada |
| 409 | Pedido PENDING/CANCELLED; no es válido preparar envío |
| 503 | Proveedor DISABLED, opt-in manual no configurado |

## Límites de BKL-047 y transición posterior

- **BKL-048** añadirá transiciones auditables de entrega y vista de
  seguimiento al cliente con filtro estricto `user_id`. Deberá ampliar
  la lista permitida de estados por una **nueva migración** y definir
  el mecanismo de operación ADMIN; no editar V20 aplicada.
- **BKL-052** podrá conectar un proveedor real tras analizar credenciales,
  contratos, webhooks autenticados, idempotencia, PII y seguridad. Nunca
  interpretar `PENDING_MANUAL` como un paquete entregado.
- Un pedido `PENDING` o `CANCELLED` no crea tareas, y solo el conciliador
  BKL-040 puede llevarlo a `CONFIRMED`.
- Hasta BKL-048 no hay seguimiento de última milla, cancelación logística
  ni comunicaciones a clientes. El catálogo, checkout, pagos y stock
  conservan su comportamiento.

## QA final (aplazado según el equipo)

Validar 401/403, CONFIRMED/PENDING/CANCELLED, proveedor desactivado,
dos administradores concurrentes, reenvío idempotente, migraciones V1-V20,
inmutabilidad del registro, ausencia de PII y falta de efectos sobre
`orders.status`, precios, pago o stock. Mantener la CI existente.
La aprobación de contrato y la revisión independiente de DoD se realizarán
con el equipo antes de integrar la PR.
