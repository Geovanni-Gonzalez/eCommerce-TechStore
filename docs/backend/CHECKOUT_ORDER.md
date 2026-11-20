# BKL-033 — Pedido PENDING con reserva atómica y GoF Facade

## Estado y alcance

Implementación de backend en la rama `33-bkl-033-checkout-pedido-reserva-facade` basada en el commit de `dev` con **BKL-032 fusionado**. El PR de este módulo debe superar CI y revisión antes de integrarse.

**Funcionalidad:** `POST /api/orders` recibe exclusivamente la dirección de entrega del cliente autenticado, recupera los productos de su carrito, recalcula precios/impuestos/envío con la **Strategy** de BKL-032, crea los snapshots de pedido de BKL-030, reserva existencias y vacía el carrito. Todas las operaciones ocurren dentro de **una única transacción de PostgreSQL**.

**No procesa pagos reales, no confirma pagos, no despacha productos y no devuelve una factura fiscal.** El pedido queda en estado `PENDING`, con `paymentMode=SIMULATED`.

## Contrato HTTP

`POST /api/orders` necesita `Authorization: Bearer <token>` de un usuario con rol `CLIENT`.

Body válido:

```json
{
  "shippingAddress": {
    "province": "San José",
    "canton": "Central",
    "district": "Carmen",
    "details": "Frente a la plaza central, casa azul",
    "recipientName": "Ana Pérez",
    "phone": "8888-1111"
  }
}
```

Los campos `userId`, `cartId`, `items`, `totalCrc`, `unitPriceCrc`, `stock`, `status`, `paymentMode` y cualquier otro campo adicional **se rechazan**. El cliente no puede escoger precio, rol o política de envío. El usuario se obtiene exclusivamente de `SessionPrincipal.id()`; los nombres, cantidades y precios vienen de la base de datos y del carrito perteneciente a ese principal.

Respuesta: **201 Created** con cabecera `Location: /api/orders/{id}`, `Cache-Control: no-store` y un `OrderResponse` sin el ID del usuario. Incluye `id`, `status:PENDING`, `paymentMode:SIMULATED`, `items` (con nombre, cantidad y precio histórico), `shippingAddress`, `totals` y `createdAt`. La ruta de detalle `GET /api/orders/{id}` **aún está planificada** (BKL-042), aunque la respuesta de creación ya contiene el pedido completo.

| HTTP | Código estable | Causa |
| --- | --- | --- |
| 201 | — | Pedido y reservas creados, carrito consumido |
| 400 | `VALIDATION_ERROR` | JSON inválido, dirección incompleta o atributos adicionales |
| 401 | `AUTHENTICATION_REQUIRED` | Sin token válido |
| 403 | `ACCESS_DENIED` | Token válido sin rol CLIENT |
| 409 | `CART_UNAVAILABLE` | Stock insuficiente o artículo inactivo durante confirmación |
| 422 | `EMPTY_CART` | Carrito vacío o ya consumido por otro checkout |

Los errores devuelven `application/problem+json` y no exponen SQL ni detalles internos.

## Diseño del patrón GoF Facade

```text
OrderCheckoutController (HTTP)
            |
            v
      CheckoutFacade.confirm(userId, shippingAddress)  <-- GoF Facade
            |
            +-- CheckoutStockRepository.lockCustomerCart
            +-- CartRepository.findItems
            +-- CheckoutStockRepository.lockProducts (orden id ascendente)
            +-- CartRepository.findItems (nombres/precios/stock bajo lock)
            +-- CheckoutTotalsCalculator.calculate (GoF Strategy BKL-032)
            +-- OrderRepository.saveDraft (snapshots históricos BKL-030)
            +-- CheckoutStockRepository.reserveForOrder (UPDATE condicional)
            +-- CartRepository.clear (carrito consumido)
            +-- OrderEventPublisher.orderCreated (Observer BKL-068, AFTER_COMMIT)
```

La fachada presenta **una operación de alto nivel** a quien llama y oculta la coordinación de subsistemas, tal como prescribe GoF Facade. El controlador no calcula importes, no actualiza stock y no crea entidades directamente.

### Secuencia transaccional y protección contra sobreventa

Toda `CheckoutFacade.confirm` está anotada con `@Transactional`; las llamadas JDBC usan la misma conexión/transacción gestionada por Spring.

1. Bloquear la fila propietaria en `carts` con `SELECT ... FOR UPDATE`, así como sus `cart_items` actuales. Dos POST concurrentes del mismo usuario se serializan, y una segunda confirmación encuentra el carrito vacío una vez que la primera hace commit.
2. Leer los IDs de productos; bloquear las filas de `products` **en orden ascendente** con `SELECT ... FOR UPDATE` para limitar interbloqueos.
3. Volver a leer precios, estado y existencias del catálogo después de obtener los bloqueos. Calcular montos con `CheckoutTotalsCalculator`, que verifica disponibilidad y aplica `ShippingStrategy`.
4. Construir `OrderDraft` con dirección, líneas comerciales históricas y `OrderTotals` exactos en CRC.
5. Persistir cabecera y detalles en `orders`/`order_items` (Flyway V6), inicialmente `PENDING`/`SIMULATED`.
6. Descontar existencias de cada producto con SQL condicional `UPDATE products SET stock = stock - quantity WHERE id=? AND active AND stock >= quantity`; exigir que afecte exactamente una fila e insertar una reserva `stock_reservations` por línea (Flyway V7).
7. Vaciar el carrito del cliente y publicar `OrderCreatedEvent` mediante el puerto `OrderEventPublisher` (sin observador concreto todavía). Si aparece una excepción en cualquier punto, **rollback** de pedido, detalles, stock, reservas y carrito.

La combinación de bloqueo + `UPDATE` condicional evita stock negativo aunque varios compradores compitan por la última unidad. El bloqueo de carrito previene la doble confirmación del mismo usuario. No se acepta un monto previamente cotizado como garantía de precio.

### Persistencia Flyway V7

`stock_reservations(order_id, product_id, quantity, status, reserved_at, updated_at)`

- PK compuesta `(order_id,product_id)`, FK a pedido y producto con `ON DELETE RESTRICT`.
- `quantity` de 1 a 99 y estados `RESERVED`, `RELEASED`, `CONSUMED`.
- Un pedido solo puede reservar una vez cada producto, sin filas huérfanas.
- El repositorio incluye `releaseReservations(orderId)` para devolver stock **solo desde RESERVED**, bajo bloqueo; repetir la operación no devuelve stock dos veces.
- `consumeReservations(orderId)` marca `CONSUMED` y no vuelve a descontar ni restituir inventario. Liberar luego una reserva consumida no tiene efecto.

**Límite relevante:** los métodos de liberar/consumir están preparados en persistencia, **pero todavía no se ofrecen como endpoints ni se integran a una máquina de estados de pedido**. La cancelación, vencimiento de reservas y auditoría de transiciones requieren BKL-027/BKL-045. Mientras tanto, las reservas PENDING pueden permanecer activas hasta que se implemente su liberación o consumo gestionados: este es un prototipo, no una solución de stock de producción.

## Principios SOLID / GRASP

- **SRP**: controller valida forma HTTP, fachada coordina negocio, calculador produce montos, repositorios ejecutan consultas y modelo conserva invariantes.
- **DIP**: la fachada depende de puertos `CartRepository`, `OrderRepository`, `CheckoutStockRepository` y `OrderEventPublisher`, además del calculador reusable.
- **OCP**: `CheckoutTotalsCalculator` depende de `ShippingStrategy`, permitiendo cambiar tarifa fija por envío gratis sobre umbral sin modificar la fachada.
- **GRASP Controller**: `OrderCheckoutController` delega en la fachada.
- **GRASP Information Expert**: `OrderDraft` y `OrderTotals` validan sus invariantes; el repositorio de inventario garantiza la condición de stock en PostgreSQL.
- **GRASP Low Coupling/High Cohesion**: módulos aislados por responsabilidades claras, DTO público separado del dominio.

## GoF Observer — BKL-068

El puerto **OrderEventPublisher** publica **OrderCreatedEvent** durante el checkout. El observador concreto **OrderAuditListener** se ejecuta **AFTER_COMMIT** y persiste una auditoría idempotente mediante **Flyway V8** y una transacción nueva. Un rollback no produce auditoría. La entrega es best effort, sin outbox ni garantía ante caída del proceso. Diagrama y pruebas: [BKL-068 — GoF Observer](ORDER_OBSERVER.md).

## Pruebas

- `CheckoutFacadeTest`: demostración de GoF Facade, secuencia de subprocesos, validaciones y evento.
- `CheckoutOrderIT`: endpoint HTTP con Spring Security real, Flyway/PostgreSQL, 201/400/401/403/409/422, snapshots, stock, consumo de carrito, aislamiento entre usuarios, liberar/consumir reserva una sola vez.
- `CheckoutConcurrencyIT`: dos compradores en **transacciones independientes** compiten por la misma unidad; exactamente uno obtiene pedido y una reserva, stock nunca negativo.
- `CheckoutRollbackIT`: inyección de fallo **después de reservar** para demostrar que no quedan pedidos, reservas ni descuentos huérfanos y el carrito se conserva.
- `scripts/ci/smoke-checkout.py`: JAR real + PostgreSQL efímero, autenticación, carrito, creación 201, stock y reintento vacío.
- `scripts/validate-openapi.test.mjs`: POST marcado como implementado, body estricto solo con dirección y autorización Bearer.

Comandos locales (PowerShell; configurar PostgreSQL aislado `techstore_test` antes de `verify`):

```powershell
git fetch origin
git switch --track origin/33-bkl-033-checkout-pedido-reserva-facade
mvn -f apps/api/pom.xml clean test
mvn -f apps/api/pom.xml clean verify
npm run validate:api-contract
npm run test:api-contract
```

## Pendientes

- BKL-027 y BKL-045: vencimiento/cancelación y liberación/consumo automático de reservas según transiciones.
- BKL-042: consultar historial y detalle de pedidos por cliente autenticado.
- BKL-068: Observer AFTER_COMMIT y evidencia SQL implementados; un transactional outbox fiable sigue fuera del alcance.
- BKL-031: validación ampliada de dirección en frontend y guía de envío.
- UI de checkout integrada en Next.js, accesibilidad y demostración MVC integral.
- Validación fiscal, seguridad/antifraude, disponibilidad real, HTTPS y observabilidad para cualquier uso productivo.

Este PR no modifica `main` ni realiza pagos reales.
