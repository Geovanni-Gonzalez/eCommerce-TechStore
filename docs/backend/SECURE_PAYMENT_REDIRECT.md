# BKL-037 — Inicio seguro de pago y redirección al sandbox

## Alcance

TechStore todavía no dispone de una pasarela bancaria ni de un inicio de sesión web operativo. **Este flujo es un checkout alojado por el propio prototipo**, usando `PaymentGateway` SANDBOX de BKL-035, intentos idempotentes de BKL-036 y futuras notificaciones firmadas de BKL-038. Un resultado `SIMULATED_APPROVED` **no constituye dinero recibido**, no modifica `orders.status` y no consume inventario. BKL-040 sigue pendiente.

## Configuración segura (opt-in)

En desarrollo local, el backend y Next.js deben correr en `localhost:8080` y `localhost:3000`, con el cliente API apuntando a `NEXT_PUBLIC_API_URL=http://localhost:8080`. El proveedor está **DISABLED por defecto**; para habilitarlo explícitamente, en un entorno aislado:

~~~bash
export TECHSTORE_PAYMENT_PROVIDER=SANDBOX
export TECHSTORE_PAYMENT_SANDBOX_ENABLED=true
export TECHSTORE_PAYMENT_SANDBOX_OUTCOME=DECLINED
# Debe figurar en CORS_ALLOWED_ORIGINS; no se acepta returnUrl del cliente.
export TECHSTORE_PAYMENT_SANDBOX_REDIRECT_ORIGIN=http://localhost:3000
mvn -f apps/api/pom.xml spring-boot:run
~~~

`DECLINED` es el valor seguro por defecto para el sandbox. Para mostrar una aprobación *artificial*, configurar `TECHSTORE_PAYMENT_SANDBOX_OUTCOME=APPROVED` en pruebas exclusivamente. En producción solo se admiten orígenes HTTPS explícitos en la lista CORS. HTTP se permite únicamente para `localhost` y `127.0.0.1` en desarrollo local.

## HTTP

`POST /api/orders/{orderId}/payment-sessions` requiere **Bearer CLIENT propietario**, header `Idempotency-Key` (8–80 caracteres alfanuméricos, `_` o `-`), **sin body**. El servidor no admite `totalCrc`, moneda, tarjeta, `returnUrl` ni `successUrl`. Obtiene pedido `PENDING`, `total_crc` `NUMERIC(14,2)`, moneda **CRC** y estado/TTL de las reservas desde PostgreSQL, con lock del pedido en orden común BKL-027/036.

El primer inicio produce `201 Created`, el replay de la misma clave devuelve `200 OK`, sin crear otro intento. Se rota el token de sesión en cada replay: el enlace anterior deja de funcionar para evitar que una URL compartida vuelva a quedar activa. Dos claves distintas no pueden crear sesiones simultáneas para un pedido cuyo intento está `STARTED`. Si el pedido se cancela, las reservas están vencidas o el intento ya se usó, se responde `409`; pedidos ajenos responden `404`.

Ejemplo de respuesta del primer inicio (valores ficticios):

~~~json
{
  "attemptId": 42,
  "orderId": 10,
  "totalCrc": 2500.00,
  "currency": "CRC",
  "expiresAt": "2026-11-21T15:00:00Z",
  "redirectUrl": "http://localhost:3000/pagos/sandbox#session=<TOKEN_ALEATORIO>"
}
~~~

El cliente navega a `redirectUrl` sin modificarla. El servidor **jamás acepta** una URL de retorno arbitraria: la construye con un origen de configuración permitido y una ruta fija de TechStore.

La página `/pagos/sandbox` lee el token desde el **fragmento** `#session=...` (el navegador no envía ese fragmento en la petición GET), elimina enseguida el fragmento del historial y **no guarda el token en cookies, localStorage ni sessionStorage**. Solo lo conserva en memoria mientras dure la simulación.

Con la clave opaca de **256 bits** en el cuerpo JSON (`{"token":"..."}`), la página llama a:

- `POST /api/payments/sandbox/sessions/preview`: devuelve `attemptId`, `orderId`, importe original en CRC y `expiresAt`, sin nombre, dirección, tarjeta ni token.
- `POST /api/payments/sandbox/sessions/complete`: consume irreversiblemente el token y llama **una sola vez** al sandbox; devuelve el `PaymentAttempt` persistente. El usuario **no escoge** `APPROVED`/`DECLINED`: el backend lo determina desde la configuración del gateway.

Los endpoints de preview y complete no requieren Bearer porque su autenticación **es la posesión de la clave efímera de 256 bits**, sin cookies. Los cuerpos solo admiten la propiedad `token`. La sesión expira como máximo en **10 minutos** o antes si vence la primera reserva. Los tokens se almacenan solo como SHA-256; la respuesta no se almacena en caché (`Cache-Control: no-store`, `Referrer-Policy: no-referrer`). Para evitar revelar el token en la URL enviada por HTTP, la página usa fragmento, no query string. Si un token se extravía, solo el CLIENT propietario puede obtener otro enlace, con la misma `Idempotency-Key` y mientras el intento continúe `STARTED`.

## Transacciones y estados conciliables

~~~mermaid
sequenceDiagram
    actor Cliente as CLIENT autenticado
    participant API as SandboxCheckoutSessionService
    participant DB as PostgreSQL V11/V13
    participant Web as /pagos/sandbox
    participant Gateway as PaymentGateway SANDBOX
    Cliente->>API: POST /orders/{id}/payment-sessions + Bearer + clave
    API->>DB: BEGIN, FOR UPDATE pedido, claim STARTED y token hash
    DB-->>API: COMMIT intento+sesión
    API-->>Cliente: redirectUrl fija #session=token
    Cliente->>Web: Navegación local (el fragmento no viaja en GET)
    Web->>API: POST preview {"token":"..."}
    API->>DB: Verificar TTL, intento, reserva, estado
    API-->>Web: CRC, importe, TTL
    Web->>API: POST complete {"token":"..."}
    API->>DB: BEGIN, FOR UPDATE pedido, canjear token único
    DB-->>API: COMMIT token consumido
    API->>Gateway: authorize(orderId, totalCRC, clave interna)
    Gateway-->>API: APPROVED/DECLINED simulados
    API->>DB: UPDATE payment_attempts (resultado)
    API-->>Web: Intento persistente (SIN confirmar order)
~~~

Si el backend cae tras canjear el token y antes de persistir la respuesta, el intento permanece `STARTED` y **no se autoriza automáticamente de nuevo**: requiere conciliación. Si el sandbox falla ambiguamente se marca `UNKNOWN`. La operación se serializa para que dos llamadas a `complete` con el mismo token jamás ejecuten dos autorizaciones. Tampoco se libera/consume stock: un pago tardío después de cancelar el pedido debe conciliarse en BKL-040.

## Validaciones

- `SandboxSessionTokenTest`: tokens impredecibles, SHA-256 y bloqueo de redirecciones externas, rutas inesperadas, userinfo y esquemas peligrosos.
- `SandboxCheckoutSessionIT`: PostgreSQL real, claim/sesión atómicos, monto CRC, clave repetida rota token, clientes ajenos, caducidad/cancelación, concurrencia de inicio y canje, rollback y estado PENDING.
- `SandboxCheckoutSessionHttpIT`: control Bearer CLIENT/ADMIN, preview/complete por token, rechazo de importes, monedas, tarjetas o returnURL, redirección fija.
- `SandboxCheckoutSessionDisabledIT`: configuración por defecto no expone endpoints.
- CI Java 21/Flyway V13 + frontend Next.js/TypeScript/OpenAPI de **26** operaciones.

**Ningún resultado del sandbox es recibo bancario ni prueba de liquidación.** Una futura pasarela externa necesitaría su API real, credenciales protegidas, webhooks verificables, redirecciones seguras específicas de proveedor, controles operativos y reconciliación.
