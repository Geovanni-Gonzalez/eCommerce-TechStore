# BKL-035 — PaymentGateway y adaptador sandbox

## Objetivo y alcance

Separar el **puerto de autorización** del resto de checkout de TechStore. El sistema continúa siendo un prototipo: **no cobra dinero, no maneja tarjetas ni se integra con procesadores externos**. La creación de pedido en BKL-033 sigue terminando en `PENDING`; la administración BKL-045 no puede forzar `CONFIRMED`.

## Contrato y diagrama

| Componente | Responsabilidad |
|---|---|
| `PaymentGateway` | Puerto `authorize(request)`, sin dependencia de Spring ni HTTP |
| `PaymentAuthorizationRequest` | `orderId`, `totalCrc` y `idempotencyKey` generados/validados en servidor |
| `PaymentAuthorization` | `outcome` + `providerReference` sin datos sensibles |
| `DisabledPaymentGateway` | Proveedor por defecto: rechaza explícitamente cualquier autorización |
| `SandboxPaymentGateway` | Resultado determinista y sin red: `APPROVED` o `DECLINED` simulados |
| `PaymentGatewayConfig` | Selecciona proveedor por configuración, obliga a opt-in del sandbox y rechaza valores desconocidos |

~~~mermaid
classDiagram
    class PaymentGateway {
        <<interface>>
        +authorize(PaymentAuthorizationRequest) PaymentAuthorization
    }
    class DisabledPaymentGateway {
        +authorize(request)
    }
    class SandboxPaymentGateway {
        +authorize(request)
    }
    class PaymentAuthorizationRequest {
        +orderId
        +totalCrc
        +idempotencyKey
    }
    class PaymentAuthorization {
        +outcome
        +providerReference
    }
    PaymentGateway <|.. DisabledPaymentGateway
    PaymentGateway <|.. SandboxPaymentGateway
    PaymentGateway ..> PaymentAuthorizationRequest
    PaymentGateway ..> PaymentAuthorization
~~~

El **monto CRC** se representa con `BigDecimal` a dos decimales, estrictamente positivo. La clave de intento solo admite `[A-Za-z0-9][A-Za-z0-9_-]{7,79}` y **no es** un token de autenticación ni credencial de procesador. Un intento idéntico produce la **misma referencia** `SBX-...` derivada de SHA-256, sin invocar redes ni almacenar datos.

La referencia determinista no es garantía financiera de idempotencia: **BKL-036** debe persistir un registro de intento, exigir unicidad de clave por ámbito, rechazar conflicto de parámetros y guardar la referencia de proveedor. BKL-038 validará callbacks/webhooks del proveedor pertinente, y **BKL-040** consumirá reservas y confirmará el pedido en transacción una vez acreditado un pago confiable. El estado simulado `APPROVED` **no prueba que alguien haya pagado dinero**.

## Configuración y seguridad

Sin variables adicionales:

~~~text
TECHSTORE_PAYMENT_PROVIDER=DISABLED
TECHSTORE_PAYMENT_SANDBOX_ENABLED=false
TECHSTORE_PAYMENT_SANDBOX_OUTCOME=DECLINED
~~~

Se rechazan todos los intentos con excepción `PaymentGatewayUnavailableException`. Para probar exclusivamente la implementación sandbox:

~~~bash
export TECHSTORE_PAYMENT_PROVIDER=SANDBOX
export TECHSTORE_PAYMENT_SANDBOX_ENABLED=true
export TECHSTORE_PAYMENT_SANDBOX_OUTCOME=DECLINED
mvn -f apps/api/pom.xml spring-boot:run
~~~

Para demostrar la respuesta positiva artificial, ajustar `TECHSTORE_PAYMENT_SANDBOX_OUTCOME=APPROVED`. **Ninguna configuración habilita cargos reales**. No existe endpoint HTTP para pagos en BKL-035: el adaptador se prueba a través de Java y la CI, no mediante navegador. Tampoco se agregan credenciales al repositorio. Una futura integración real debe recibir secretos únicamente desde un gestor de secretos/entorno seguro y nunca imprimirlos en logs, persistir PAN/CVV ni confiar en montos enviados por el cliente.

La selección de proveedor es **fail-closed**: `DISABLED` por defecto; `SANDBOX` exige `enabled=true`; proveedores desconocidos o resultados sandbox no admitidos impiden inicializar el bean. Esto evita activar accidentalmente una simulación aprobatoria en entornos no preparados.

## Validación y demostración

- `PaymentGatewayTest`: monto exacto y validación de clave, configuración segura, resultados aprobados/rechazados de simulación, referencia reproducible y diferencia de referencias ante variación de intento/total/pedido.
- `PaymentGatewayWiringIT`: arranque Spring Boot completo con el proveedor por defecto `DISABLED`; demuestra que no se activa un sandbox por omisión.
- CI: `mvn -f apps/api/pom.xml clean verify`, PostgreSQL `techstore_test`, validación OpenAPI y smoke HTTP. **No hay rutas nuevas** ni migraciones SQL en BKL-035.

### Exclusiones expresas

No hay cobros, callbacks, registro de intentos ni cambio de `orders.status`. Se implementarán en BKL-036, BKL-038 y BKL-040 respectivamente. El proveedor simulado no debe ser utilizado como evidencia de liquidación de pagos reales.
