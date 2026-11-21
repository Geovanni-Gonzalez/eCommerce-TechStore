# BKL-041 — Rechazo, vencimiento seguro y conciliación de autorizaciones inciertas

## Propósito

El sistema es un **prototipo académico**, con pasarela SANDBOX o DISABLED; **no procesa pagos reales**. Un webhook HMAC válido del sandbox no es prueba bancaria de fondos. Este diseño establece una regla conservadora: **nunca soltar inventario mientras pueda existir una autorización pendiente, ambigua o aprobada aún sin conciliar**. El sistema evita deducir falsamente que un fallo de red significa rechazo.

## Tabla de política por estado (Flyway V15)

| Evidencia del pedido en `PENDING` | Cancelación CLIENT/ADMIN | Vencimiento de reserva | Motivo |
|---|---|---|---|
| Sin intentos de pago | Permitida | `RELEASED`, pedido `CANCELLED` | No hubo solicitud de cobro |
| `UNAVAILABLE` o `SIMULATED_DECLINED` sin señales en conflicto | Permitida | Liberar una vez al vencer | No hay aprobación |
| `STARTED` **con sesión hosted de BKL-037 nunca canjeada** | Permitida | Liberar al vencer | El flujo hosted nunca llamó `authorize()` |
| `STARTED` sin sesión, o con sesión ya canjeada | Bloqueada (409) | Retener `RESERVED`, registrar incidencia | Gateway pudo haber sido invocado |
| `UNKNOWN` | Bloqueada (409) | Retener `RESERVED`, registrar incidencia | Resultado no verificable |
| `SIMULATED_APPROVED` sin confirmación completa | Bloqueada (409) | Retener `RESERVED`, registrar incidencia | No cancelar frente a aprobación pendiente |
| Webhook `NEEDS_REVIEW` o evidencia contradictoria | Bloqueada (409) | Retener, registrar incidencia | Requiere revisión antes de liberar |

Se priorizan en el scan de vencimiento los pedidos **sin posibles autorizaciones activas** para impedir que muchos pedidos retenidos obstaculicen el resto. Los bloqueos siguen `orders FOR UPDATE → payment_attempts → sandbox_checkout_sessions / stock_reservations` y se comparte orden con cancelación BKL-027, intento BKL-036 y confirmación BKL-040. Los cambios en inventario, historial e incidencias tienen transacciones SQL reales; `ON CONFLICT DO NOTHING` impide duplicar incidencias.

La reserva `RESERVED` descuenta existencias desde BKL-033: **retenerla no vuelve a descontar**; liberar repone las unidades una única vez. No hay expiración automática del estado `UNKNOWN`: un contador temporal no prueba que no se capturaron fondos. La política es conservadora y puede mantener existencias retenidas hasta que exista una integración verificable de conciliación.

## Incidencias V15

`payment_reconciliation_incidents` guarda `order_id`, motivo, vínculo opcional al webhook, fecha de detección y unicidad `(order_id,reason)`. Se registran estos motivos:

- `AMBIGUOUS_AUTHORIZATION`: `STARTED` potencialmente ejecutado o `UNKNOWN` al vencer.
- `AWAITING_VERIFIED_APPROVAL`: autorización simulada aprobada que no se ha conciliado con el pedido.
- `LATE_APPROVAL`: webhook firmado `APPROVED` después de cancelación/vencimiento o reserva ya liberada. Se registra incluso si `TECHSTORE_PAYMENT_SANDBOX_AUTO_CONFIRM=false`.
- `EVIDENCE_MISMATCH`: importes, reservas, resultados u otros datos incompatibles; BKL-040 marca `NEEDS_REVIEW`.

Una incidencia **no cambia por sí misma** el estado `orders`, del intento ni de la reserva, ni desbloquea automáticamente el inventario. Los registros son permanentes por ahora; BKL-041 **no incluye** una operación para declarar `UNKNOWN` como "sin cobro" ni para forzar `CONFIRMED`. Eso requiere evidencia externa autenticada en una futura integración real.

## API y responsabilidades operativas

`GET /api/admin/orders/{orderId}/payment-incidents` (Bearer ADMIN) devuelve lista de `PaymentIncident` con `id`, `orderId`, `reason`, `webhookEventId` (nullable) y `detectedAt`. No incluye tarjetas, correo, dirección ni secreto HMAC; `Cache-Control: no-store`. Las solicitudes anónimas y CLIENT reciben 401/403; pedidos inexistentes 404.

La cancelación usual de pedido `POST /api/orders/{orderId}/cancel` y el endpoint ADMIN existente fallan con 409 cuando hay riesgo. El job automático de expiración **no cancela** en estos casos y guarda la incidencia para consulta. La respuesta del webhook firmado se mantiene idempotente en BKL-038; BKL-040 solo confirma en SANDBOX explícitamente habilitado y aún puede indicar `NEEDS_REVIEW`.

Un callback `APPROVED` tardío no restaura stock ni altera un pedido cancelado. Si el pedido fue cancelado antes de implementar esta protección o una integración externa notifica tarde, se registra incidencia `LATE_APPROVAL` para posible compensación manual. No inventar una captura ni reintentar el mismo intento `STARTED/UNKNOWN` automáticamente.

## Validación

- `PaymentExposureLifecycleIT`: PostgreSQL real con reservas vigentes/vencidas, `UNAVAILABLE`, `SIMULATED_DECLINED`, `STARTED` directo o hosted, `UNKNOWN`, aprobación pendiente, idempotencia de incidencias y autorización ADMIN.
- `SandboxPaymentConfirmationIT`: adaptar caso de cancelación heredada, webhook tardío y reserva vencida para verificar `NEEDS_REVIEW` + incidencia, stock intacto y prevención de cancelaciones nuevas inseguras.
- OpenAPI **28 operaciones**, validaciones negativas de auth y contrato, CI backend/frontend y smoke.

**Límites:** sin operación de reembolso bancario, sin adquirente, sin conciliación externa autoritativa ni liberación de pagos inciertos por el transcurso del tiempo. Estas capacidades deberán diseñarse en una tarea de integración productiva independiente.
