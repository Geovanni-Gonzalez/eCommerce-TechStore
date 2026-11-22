# BKL-048 — Seguimiento de envío y estado visible

## Decisión de producto

El checkout y la conciliación sandbox crean pedidos `CONFIRMED`, pero **no
confirman por sí solos un despacho físico**. El registro BKL-047 comienza
como `PENDING_MANUAL` y exige un administrador que verifique el trabajo
realizado antes de declarar un hito. Los estados son manuales; **no se
presentan como pruebas de una transportista ni ubicaciones en tiempo real**.

## Máquina de estados y concurrencia

```text
Pedido CONFIRMED -> sin tarea (NOT_REGISTERED solo en vista cliente)
                 -> PENDING_MANUAL -> IN_TRANSIT -> DELIVERED
```

BKL-048 no permite saltar etapas, retroceder, cambiar un DELIVERED ni crear
tareas para pedidos PENDING/CANCELLED. Enviar dos veces el mismo estado
de forma consecutiva es idempotente: no duplica movimientos. En cualquier
caso, el endpoint no modifica pagos, `orders.status`, stock ni reservas.

Flyway **V21** amplía `shipments` sin editar V20 y crea
`shipment_status_history` con estado previo, estado nuevo, actor ADMIN,
motivo fijo y fecha. Inserta la apertura de filas históricas V20 sin
inventar envíos anteriores. Triggers validan transiciones, impiden UPDATE/
DELETE de historial y escriben cada hito dentro de la transacción de SQL
que actualiza el estado. El servicio bloquea `orders FOR UPDATE` como en
BKL-045 y BKL-040; se serializan dos operadores concurrentes.

## Endpoints

| Método | Ruta | Rol |
| --- | --- | --- |
| GET | `/api/orders/{orderId}/shipping` | CLIENT propietario |
| GET | `/api/admin/shipments/orders/{orderId}` | ADMIN |
| POST | `/api/admin/shipments/orders/{orderId}` | ADMIN |
| GET | `/api/admin/shipments/orders/{orderId}/history` | ADMIN |
| PATCH | `/api/admin/shipments/orders/{orderId}/status` | ADMIN |

El PATCH acepta únicamente `{"status":"IN_TRANSIT"}` o
`{"status":"DELIVERED"}`, sin elegir actor, destinatario, importe o
transportista. La API decide el actor desde `SessionPrincipal`.

El GET CLIENT usa **WHERE id=? AND user_id=?** antes de leer la tarea. Un
pedido ajeno y uno inexistente devuelven 404 indistinguible. Para pedidos
propios sin tarea retorna `NOT_REGISTERED` con historial vacío. La vista
cliente incluye solo identificador de pedido, estado, fecha y hitos; **no
expone actorUserId, dirección, teléfono ni notas libres**. Las respuestas
privadas envían `Cache-Control: no-store`.

## Interfaz

En `/pedidos` se muestra seguimiento legible por el cliente con estados
y cronología, marcado explícitamente como **registro manual**. El token
CLIENT se mantiene en memoria.

En `/admin`, un pedido confirmado admite abrir una gestión manual,
registrar despacho y registrar entrega. Cada paso exige una confirmación
explícita y muestra su historial con actor y fecha. No permite estas
operaciones para pedidos no confirmados. `DISABLED` bloquea mutaciones;
`MANUAL` exige activar `TECHSTORE_SHIPPING_PROVIDER=MANUAL`.

## Aceptación pendiente para QA final

Autorización anónima/CLIENT/ADMIN, IDOR y respuesta 404 indistinguible,
prohibición de saltos y regresiones, reintentos y concurrencia de dos ADMIN,
auditoría inmutable y rollback, V20→V21 con filas existentes, servidor
con proveedor DISABLED, flujo real MANUAL, accesibilidad teclado/móvil y
ausencia de cambios en pagos, pedidos y stock. La CI existente se mantiene.

La integración o simulación con proveedor externo sigue reservada para
BKL-052. Nunca se simulan números de guía.
