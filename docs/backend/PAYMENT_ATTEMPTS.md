# BKL-036 — Intentos de pago persistentes e idempotentes

## Alcance y seguridad

BKL-036 conecta el puerto `PaymentGateway` de BKL-035 a **intentos persistentes** de un pedido del cliente autenticado. Solo existen dos proveedores: `DISABLED` por defecto (siempre devuelve `UNAVAILABLE` sin cobro) y `SANDBOX` opt-in (sin red ni dinero real). **SIMULATED_APPROVED no confirma el pedido ni acredita pago bancario.** BKL-038 verifica eventos y BKL-040 deberá implementar consumo de inventario/CONFIRMED tras una acreditación fiable.

## API

`POST /api/orders/{orderId}/payment-attempts` con `Authorization: Bearer <token>`, rol CLIENT e `Idempotency-Key: attempt-2026-0001`, sin body. El servidor lee el usuario y el `total_crc` del snapshot de `orders`, nunca acepta montos o datos de tarjeta desde el navegador.

- **201 Created:** se insertó el intento; `Location` apunta a `GET /api/orders/{orderId}/payment-attempts/{attemptId}`. El body muestra estado persistido, monto CRC, identificador y referencia simulada o null.
- **200 OK:** la misma clave del mismo cliente/pedido devuelve el registro guardado y **no invoca de nuevo** la pasarela. Incluso `STARTED` y `UNKNOWN` se devuelven sin reintentar.
- **400:** falta clave, formato incorrecto o se envió body (incluyendo precio o información de tarjeta).
- **401/403:** sin autenticación o sin rol CLIENT.
- **404:** pedido inexistente/ajeno o intento no visible para el propietario.
- **409:** clave usada por ese cliente con otro pedido, pedido sin reserva vigente, o existe un intento `STARTED`, `UNKNOWN` o `SIMULATED_APPROVED` con otra clave.

`GET /api/orders/{orderId}/payment-attempts/{attemptId}` permite consultar la persistencia y no llama al proveedor. Ambos endpoints usan `Cache-Control: no-store`; las respuestas nunca incluyen `Idempotency-Key` ni su hash.

## Esquema Flyway V11

La tabla `payment_attempts` conserva `order_id` + `user_id` con clave foránea compuesta a `orders`, `idempotency_key_hash CHAR(64)` (SHA-256, no se guarda la clave original), `total_crc`, `status`, `provider_reference` única, y fechas de creación/actualización. Una restricción `UNIQUE(user_id, idempotency_key_hash)` evita que el mismo cliente use una clave en otro pedido.

Un índice parcial `UNIQUE(order_id)` para `STARTED`, `SIMULATED_APPROVED` y `UNKNOWN` evita múltiples autorizaciones activas/aprobadas/inciertas sobre un mismo pedido. Los resultados `SIMULATED_DECLINED` y `UNAVAILABLE` permiten una clave nueva.

## Flujo transaccional y concurrencia

~~~mermaid
sequenceDiagram
    actor C as CLIENT
    participant API as PaymentAttemptService
    participant DB as PostgreSQL
    participant GW as PaymentGateway
    C->>API: POST con Idempotency-Key
    API->>DB: BEGIN; SELECT orders FOR UPDATE
    DB-->>API: Dueño, estado y monto
    API->>DB: Reutilizar clave o INSERT STARTED
    DB-->>API: COMMIT del claim
    alt Primera clave
        API->>GW: authorize(orderId,total,key)
        alt Respuesta simulada
            GW-->>API: APPROVED/DECLINED simulado + ref
            API->>DB: UPDATE SIMULATED_* + referencia
        else Proveedor apagado
            GW-->>API: No disponible
            API->>DB: UPDATE UNAVAILABLE
        else Resultado ambiguo
            GW-->>API: Excepción no reconocida
            API->>DB: UPDATE UNKNOWN, sin reintentar
        end
    else Reintento de clave conocida
        Note over API,GW: El gateway NO recibe otra llamada
    end
    API-->>C: 201/200 + estado durable
~~~

El **primer commit ocurre antes** de ejecutar el gateway y **la segunda transacción registra el resultado**. Una caída después del primer commit deja `STARTED`, que puede recuperarse por GET o replay y **no se reenvía automáticamente**. Si el gateway falla de modo ambiguo, `UNKNOWN` bloquea nuevas claves hasta revisión. No se confunde "servicio deshabilitado" con autorización rechazada.

Orden de bloqueos: `orders FOR UPDATE` antes de consultar reservas e intentos; compatible con cancelación/expiración BKL-027. Se rechazan nuevas autorizaciones para pedidos CANCELLED/CONFIRMED o reservas vencidas. Si ocurre cancelación mientras una llamada a un proveedor estuviera en vuelo, el pedido no se confirma en BKL-036 y el intento permanece auditable para conciliación; BKL-040 debe implementar política explícita de pagos tardíos/reembolsos.

## Pruebas

- `PaymentAttemptIT` (PostgreSQL real, commits independientes): creación, reintento, hash, rechazo de otro cliente/otro pedido, dos hilos con una sola llamada, estados desconocido e iniciado, aprobación simulada que no confirma, declinación, expiración, cancelación y rollback.
- `PaymentAttemptHttpIT`: Bearer CLIENT/ADMIN, validación de body y clave, propiedad, Location, 201/200/404 y consulta segura.
- `WebCorsConfigTest`: autorización del header `Idempotency-Key` para frontend.
- `mvn -f apps/api/pom.xml clean verify` en PostgreSQL `techstore_test` + validación OpenAPI y smoke.

## Límites explícitos

No existe integración bancaria ni autorización de cobro real. La idempotencia de **solicitudes** a TechStore se garantiza mediante la base y la no repetición de una clave; un proveedor externo futuro también deberá aceptar una clave propia, confirmaciones verificadas, conciliación operacional y controles anti-cobro duplicado. No enviar PAN/CVV; las claves no son credenciales. `SIMULATED_APPROVED` es artificial y nunca debe utilizarse para pasar `orders.status` a `CONFIRMED` sin desarrollar y validar BKL-038/BKL-040.


## BKL-038 — Webhooks auténticos de sandbox

Un webhook firmado con HMAC-SHA256 y timestamp reciente se acepta únicamente al coincidir con un intento simulado persistido. V12 registra el evento deduplicado y su digest. No transforma la autorización sandbox en pago bancario ni cambia el pedido. Documentación: [WEBHOOK_VERIFICATION.md](WEBHOOK_VERIFICATION.md).


## Inicio de pago con redirección (BKL-037)

El endpoint `POST /api/orders/{orderId}/payment-sessions` (CLIENT, Idempotency-Key, sin body) crea un intento STARTED y un token de sesión local sin llamar todavía al gateway. La redirección segura a `/pagos/sandbox` se canjea una sola vez en `/api/payments/sandbox/sessions/complete`. [Detalles y seguridad](SECURE_PAYMENT_REDIRECT.md).


## BKL-040

El resultado `SIMULATED_APPROVED` del intento **no** confirma automáticamente el pedido; se requiere el evento HMAC de BKL-038 y un reconciliador transaccional opt-in. Los errores conservan el evento para conciliación y nunca crean un doble consumo de la reserva. Véase [ATOMIC_PAYMENT_CONFIRMATION.md](ATOMIC_PAYMENT_CONFIRMATION.md).
