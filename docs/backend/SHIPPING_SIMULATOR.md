# BKL-052 — Simulador controlado de envíos

Issue: https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/53

## Decisión del proveedor

Se adopta **SIMULATOR local, no integración logística real**, debido a que el
prototipo no tiene contrato comercial, credenciales ni webhooks verificables
de una transportista. Se conserva el puerto `ShippingGateway` de BKL-047 y
la auditoría BKL-048. No hay conexiones HTTP externas, teléfonos, direcciones
copiadas ni guías inventadas. No se envían paquetes ni notificaciones externas.

Los tres modos configurables son:
- `DISABLED` (por defecto): ningún registro ni transición permitidos;
  responde con Problem Details 503.
- `MANUAL`: el operador ADMIN declara **operaciones físicas verificadas**;
  los eventos conservan razones `MANUAL_*`.
- `SIMULATOR`: solo para demostración, genera hitos **ficticios** con
  razones `SIMULATOR_*`. El seguimiento CLIENT y el panel ADMIN
  muestran explícitamente el origen `SIMULATOR`. **Un estado DELIVERED
  del simulador NO acredita una entrega real y no debe habilitar reseñas
  por compra entregada** (BKL-050 deberá excluir esta fuente).

`ShippingStrategy` calcula el precio de envío al checkout y **no
interactúa** con `ShippingGateway`.

## Configuración segura

```bash
# Local, demostración explícita
TECHSTORE_SHIPPING_PROVIDER=SIMULATOR
TECHSTORE_SHIPPING_SIMULATOR_ENABLED=true
TECHSTORE_SHIPPING_SIMULATOR_OUTCOME=AVAILABLE
```

- Para simular fallo determinista: `TECHSTORE_SHIPPING_SIMULATOR_OUTCOME=UNAVAILABLE`.
- El valor por defecto es `DISABLED`, opt-in `false`, outcome
  `UNAVAILABLE`. Nunca se habilita por instalar el proyecto.
- `SIMULATOR` con `enabled=false` o con outcome desconocido provoca
  error de configuración en el arranque (**fail-closed**).
- En `UNAVAILABLE`, `SimulatedShippingGateway` rechaza **antes de
  ejecutar SQL**, devolviendo 503 sin alterar envío, bitácora, pedido,
  pago, reserva ni inventario.
- No existe control en el navegador para cambiar proveedor, activar la
  simulación ni seleccionar su resultado. Solo variables de entorno del
  backend permiten hacerlo. Nunca usar `SIMULATOR` como logística real.

## Persistencia y compatibilidad

Flyway **V22**, incremental sobre V20/V21, permite
`fulfillment_method=SIMULATOR` además de `MANUAL` y amplía las razones
auditables a `SIMULATOR_REGISTERED`, `SIMULATOR_DISPATCH`,
`SIMULATOR_DELIVERY`. El trigger de auditoría se actualiza para elegir
el motivo según el método **inmutable** del registro.

Los registros MANUAL existentes conservan estado, fuente y bitácora. Los
triggers BKL-048 siguen prohibiendo edición, eliminación, saltos y
regresiones de estados. `UNIQUE(order_id)` impide duplicar tareas.

Al intentar manejar una tarea MANUAL bajo un servidor configurado con
SIMULATOR (o viceversa) devuelve **409** sin cambiar el registro. Incluso
un reintento al estado actual comprueba la compatibilidad del adaptador.
No se permite convertir silenciosamente registros existentes; la política
operativa debe definirse fuera del simulador.

Las transacciones emplean `SELECT ... FOR UPDATE` de la orden y SQL
parametrizado; el INSERT/UPDATE y la auditoría PostgreSQL son atómicos.
Solo se permite registrar o avanzar una tarea cuando el pedido está
`CONFIRMED` por el conciliador; no hay camino desde la logística hacia
`orders.status`, `payment_attempts`, `products.stock` ni
`stock_reservations`.

## API y fallos controlados

Se **reutilizan** las cinco operaciones de BKL-047/048, sin añadir endpoints:
`POST /api/admin/shipments/orders/{orderId}` y
`PATCH /api/admin/shipments/orders/{orderId}/status` (ROLE_ADMIN),
consulta de registro/historia para ADMIN y
`GET /api/orders/{orderId}/shipping` para el propietario CLIENT.

El JSON incluye `fulfillmentMethod` (MANUAL o SIMULATOR;
`null` solo en `NOT_REGISTERED`). Los eventos tienen razones distintas
para cada fuente. El cliente ve texto inequívoco que niega un despacho
o entrega real en SIMULATOR. Los registros auditables incluyen actor
ADMIN; el historial CLIENT no lo incluye ni expone PII.

Fallos esperados: 401/403 autorización, 404 pedido inexistente/ajeno,
409 modo incompatible, saltos inválidos o pedido no confirmado,
503 proveedor DISABLED / simulador UNAVAILABLE. No se expone SQL,
stacktrace ni información de configuración sensible.

## QA final (según planificación del equipo)

Validar: migración V20→V21→V22 con datos MANUAL; configuración DISABLED,
MANUAL, SIMULATOR AVAILABLE/UNAVAILABLE, outcome desconocido, activación
false; simultaneidad y reintento; aislamiento por propietario; 503 sin
escrituras y 409 sin conversión de modo; auditoría correcta; textos UI;
que no se alteren pagos, pedidos, inventario, ni se habiliten reseñas por
DELIVERED simulado; accesibilidad escritorio/móvil.

La CI automática no se desactiva. La revisión y las pruebas integrales
manuales permanecen para la fase final.
