# BKL-038 — Webhook firmado y deduplicación de eventos del sandbox

## Alcance actual y dependencia

**BKL-037 implementa un inicio y redirección al sandbox local.** Por eso BKL-038 construye una **base demostrable de autenticidad y deduplicación** restringida al sandbox existente de BKL-035/BKL-036, no un webhook de un operador bancario. No existe cobro real, callback externo confiable ni cambio de estado a CONFIRMED. Esa integración requiere completar BKL-037 y los pasos verificados de BKL-040.

De manera predeterminada **no se registra el controller**: `techstore.payment.webhook.enabled=false`. Habilitarlo sin un secreto externo fuerte (32 bytes UTF-8 o más) provoca error de arranque, **no desactiva la comprobación**. No colocar secretos, firmas, cuerpos de peticiones ni datos de tarjeta en Git o registros.

## Contrato HTTP

`POST /api/payments/webhooks/sandbox`, `Content-Type: application/json` con headers:

- `X-TechStore-Webhook-Timestamp`: tiempo Unix UTC en segundos, entre **−300 y +300 segundos** del reloj del servidor.
- `X-TechStore-Webhook-Signature`: cadena `v1=` seguida de **64 caracteres hexadecimales minúsculos**, correspondiente a HMAC-SHA256 del mensaje exacto `ASCII(timestamp) + "." + rawBodyBytes`.

Body JSON firmado, sin propiedades adicionales:

~~~json
{
  "eventId": "sandbox-event-0001",
  "providerReference": "SBX-0123456789ABCDEF01234567",
  "orderId": 123,
  "totalCrc": 4195.00,
  "outcome": "APPROVED"
}
~~~

El ejemplo solo muestra el formato. `orderId`, monto, referencia e identificadores deben existir y **coincidir realmente con un intento simulado persistido** para que sea aceptado. El body es UTF-8 estricto, máximo **16 KiB**, y su firma se verifica **antes** de interpretar cualquier campo. La firma se compara en tiempo constante. El secreto nunca se recibe por body ni por respuesta; no se utiliza el token Bearer del cliente para autenticar callbacks.

- **202 Accepted:** primera entrada válida, guardada de forma transaccional (campo `duplicate=false`).
- **200 OK:** el mismo `eventId`, cuerpo exacto y referencia previa; no se añade ninguna entrada (`duplicate=true`).
- **400:** JSON, propiedad, tipo, monto o UTF-8 inválido; demasiado grande.
- **401:** falta firma, HMAC no coincide o timestamp fuera de ventana.
- **409:** referencia sin intento válido, outcome/monto distintos, eventId reutilizado con otro contenido o segundo evento terminal para el mismo intento.
- **415:** Content-Type no soportado.

Las respuestas llevan `Cache-Control: no-store`. No se retornan el secreto, la firma, la clave de idempotencia ni el payload original.

## Persistencia Flyway V12

`payment_webhook_events` almacena `provider='SANDBOX'`, `provider_event_id`, FK a `payment_attempts`, hash SHA-256 del cuerpo original, resultado APPPROVED/DECLINED y `received_at`. Se usa `UNIQUE(provider,provider_event_id)` y `UNIQUE(payment_attempt_id)` porque este protocolo demostrativo solo admite **una notificación terminal por intento**.

El adaptador JDBC busca la referencia de BKL-036, bloquea primero `orders FOR UPDATE` y luego `payment_attempts FOR UPDATE`, compara estado, monto y resultado, y registra una única entrada con `INSERT ... ON CONFLICT DO NOTHING`. La segunda petición devuelve el registro inicial si el hash y la relación coinciden; en caso contrario responde 409. Dos hilos con el mismo evento no escriben dos filas, aun con PostgreSQL real.

**No se modifica** `orders`, `order_status_history`, `stock_reservations`, `products.stock` ni `payment_attempts.status`. Un evento puede llegar después de cancelar un pedido; se almacena como evidencia conciliable sin asumir que debe entregarse o cobrarse. La confirmación y consumo requieren BKL-040, verificación de proveedor apropiada y política de tardíos.

## Configuración de demostración

Para un entorno de pruebas aislado, configurar **fuera del repositorio**:

~~~bash
export TECHSTORE_PAYMENT_WEBHOOK_ENABLED=true
# Ejemplo educativo: usar un secreto real aleatorio, nunca reutilizar este texto.
export TECHSTORE_PAYMENT_WEBHOOK_SECRET="$(openssl rand -hex 32)"
mvn -f apps/api/pom.xml spring-boot:run
~~~

El gateway sigue DISABLED por defecto. Para obtener antes una referencia simulada, habilitar por separado SANDBOX y crear un intento BKL-036; el endpoint de webhook **no crea** intentos. Para probar HMAC, conservar los bytes exactos del body y firmar el prefijo de timestamp más punto con el secreto de la instancia. No enviar un monto distinto al snapshot ni modificar espacios después de calcular la firma.

### Comprobación SQL

~~~sql
SELECT e.id, e.provider, e.provider_event_id, e.payment_attempt_id,
       e.outcome, e.received_at
FROM payment_webhook_events e
JOIN payment_attempts a ON a.id = e.payment_attempt_id
WHERE a.order_id = :order_id ORDER BY e.id;
~~~

Ante dos POST idénticos, se observa **una sola fila** de auditoría y ningún cambio de `orders.status` o `stock_reservations.status`.

## Pruebas y límites

- `SignedSandboxWebhookVerifierTest`: HMAC original, timestamps ±300s, firma alterada, secreto mínimo, tamaño máximo, rechazo de campos y estados inventados.
- `SandboxWebhookHttpIT`: callback sin Bearer pero con HMAC, 202/200/400/401/409, tamper, body extra, monto/referencia/estado y comprobación sin mutación de pedido/stock.
- `SandboxWebhookConcurrencyIT`: dos transacciones PostgreSQL concurrentes y unicidades de evento e intento.
- `SandboxWebhookDisabledIT`: ausencia del endpoint bajo la configuración por defecto.
- CI `mvn -f apps/api/pom.xml clean verify`, OpenAPI de 23 operaciones, frontend y pruebas de smoke.

**No usar las notificaciones de sandbox como recibos bancarios.** En un proveedor real habrá que implementar su propio mecanismo documentado de firma/validación, claves con rotación y política de conciliación antes de confirmaciones. La idempotencia de webhooks no sustituye la idempotencia financiera de BKL-036.
