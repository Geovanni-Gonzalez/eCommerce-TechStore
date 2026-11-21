# BKL-040 — Confirmación y consumo atómico de reserva ante evento verificado

## Seguridad y alcance: SOLO sandbox, no bancario

El único proveedor actual del repositorio es `SANDBOX` (o `DISABLED`). BKL-038 solo autentica notificaciones con HMAC configurado por TechStore, **no verifica liquidación en una entidad bancaria**. Por seguridad, **BKL-040 NO confirma pedidos por defecto**. Para demostrar la transición se necesita activarla explícitamente en un entorno de pruebas aislado y el registro `orders.payment_mode` permanece en `SIMULATED`. `CONFIRMED_SIMULATED` no es evidencia de dinero cobrado. No conectar esta modalidad a despacho, facturación ni fulfillment real.

~~~bash
# Demostración aislada de API, sandbox y webhook firmado (no producción)
export TECHSTORE_PAYMENT_PROVIDER=SANDBOX
export TECHSTORE_PAYMENT_SANDBOX_ENABLED=true
export TECHSTORE_PAYMENT_SANDBOX_OUTCOME=APPROVED
export TECHSTORE_PAYMENT_WEBHOOK_ENABLED=true
export TECHSTORE_PAYMENT_WEBHOOK_SECRET="$(openssl rand -hex 32)"
export TECHSTORE_PAYMENT_SANDBOX_AUTO_CONFIRM=true
~~~

No habilitar `TECHSTORE_PAYMENT_SANDBOX_AUTO_CONFIRM` en entornos de dinero real. El servicio aborta el arranque cuando se activa sin SANDBOX + webhook HMAC. En configuración ordinaria el flag está en `false`.

## Flujo exacto

1. BKL-036 registra un intento idempotente (`payment_attempts`) con monto histórico del pedido, en `CRC`; BKL-037 habilita redirección local de un solo uso. Solo el sandbox puede producir `SIMULATED_APPROVED`.
2. BKL-038 recibe el webhook, **verifica HMAC-SHA256 sobre bytes originales y timestamp**, comprueba pedido, referencia, monto y resultado y guarda el evento deduplicado en `payment_webhook_events` con `reconciliation_status=RECEIVED`. Su **commit ocurre antes de reconciliar**.
3. Si el modo demo está habilitado, BKL-040 intenta reconciliar inmediatamente el evento en **otra transacción**. Primero obtiene el pedido sin bloqueo, luego `SELECT orders FOR UPDATE`; bloquea el intento y el evento, y finalmente las reservas ordenadas. El orden es compatible con cancelación/vencimiento BKL-027.
4. Solo si el evento es `APPROVED` del proveedor `SANDBOX`, el intento está en `SIMULATED_APPROVED`, su referencia es `SBX-...`, monto igual a `orders.total_crc`, el pedido sigue `PENDING/SIMULATED` y **cada línea histórica tiene una reserva idéntica `RESERVED` no vencida**, escribe en una única transacción:
   - `stock_reservations.status` → `CONSUMED` (exactamente todas las líneas).
   - `orders.status` → `CONFIRMED` (con `payment_mode=SIMULATED` inmutable).
   - `order_status_history`: `PENDING→CONFIRMED`, actor `SYSTEM`, razón `SANDBOX_VERIFIED`.
   - `payment_webhook_events.reconciliation_status` → `CONFIRMED_SIMULATED` y `reconciled_at`.
5. **No se decrementa `products.stock` otra vez**. BKL-033 ya lo hizo al reservar durante checkout. Consumir significa congelar esas unidades asignadas, no volver a retirarlas.
6. Si llega `DECLINED`, cambia el estado del evento a `DECLINED` sin modificar pedido o reservas. Si llega tras cancelación, expiración, discrepancia de reservas o monto, se marca `NEEDS_REVIEW`: no se confirma ni consume inventario.
7. Si una excepción interrumpe la transacción de confirmación, **se revierten conjuntamente** estado del pedido, reservas, historial y estado de conciliación. La fila del webhook sigue persistida como `RECEIVED`, disponible para una revisión/reintento operativos. La respuesta HTTP del webhook se mantiene 202/200, nunca se inventa un cobro confirmado; el backend registra solo el ID interno, sin secretos.

## Migración Flyway V14

V14 añade `payment_webhook_events.reconciliation_status` (`RECEIVED`, `CONFIRMED_SIMULATED`, `DECLINED`, `NEEDS_REVIEW`) y `reconciled_at`, con índice para eventos que requieren atención. En el historial añade el motivo `SANDBOX_VERIFIED`. Se conservan intactas las migraciones anteriores, y se marcan los eventos históricos como `RECEIVED`, no como pagados.

## Revisión manual autorizada

`POST /api/admin/payment-events/{eventId}/reconcile` (Bearer ADMIN), sin body. Acepta **solo el ID de un evento previamente recibido y verificado**; no admite precio, `APPROVED`, referencia, token, URL o cambios arbitrarios. La misma confirmación idempotente puede devolverse múltiples veces sin duplicar movimientos de inventario ni historial. Si `NEEDS_REVIEW` requiere acción humana, el endpoint informa ese estado pero no fuerza la confirmación.

El endpoint no existe salvo cuando `TECHSTORE_PAYMENT_SANDBOX_AUTO_CONFIRM=true`, y no funciona con proveedor real. Devuelve `PaymentReconciliationResult` con `eventId`, `orderId` y `status`. Respuestas `no-store`, errores 400/401/403/404 con Problem Details. Ningún cliente puede habilitar confirmaciones por URL ni elegir el resultado.

## Pruebas

- `SandboxPaymentConfirmationIT` con PostgreSQL de pruebas: evento realmente firmado que confirma una vez, replay con una sola transición, declinación, cancelación tardía y expiración -> `NEEDS_REVIEW`, discrepancia reserva vs snapshot, rollback completo, dos conciliaciones concurrentes, ADMIN/CLIENT/anon autenticación y bloqueo de cuerpos fabricados.
- `SandboxPaymentConfirmationDisabledIT`: comprueba modo desactivado por defecto.
- CI Maven Java 21/Flyway V14, PostgreSQL, test unitarios y de integración, frontend y OpenAPI de **27** operaciones.

## Trabajo pendiente para banca real

Implementar integración externa con credenciales segregadas, autenticidad y semántica del estado del procesador, idempotencia financiera, confirmaciones firmes de fondos/captura, conciliación de operaciones tardías y políticas de reintegro. El estado **`CONFIRMED` bajo `payment_mode=SIMULATED`** es **solo una confirmación de demostración** y no autoriza envío de mercancía ni facturas.


## Protección adicional de vencimiento — BKL-041

La política BKL-041 evita liberar automáticamente stock cuando hay `STARTED` ejecutado, `UNKNOWN` o `SIMULATED_APPROVED` aún sin conciliar. Webhooks tardíos y divergencias crean incidencias auditables en V15. Véase [PAYMENT_FAILURE_RECONCILIATION.md](PAYMENT_FAILURE_RECONCILIATION.md).
