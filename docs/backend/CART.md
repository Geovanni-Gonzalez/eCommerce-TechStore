# BKL-028 — Carrito persistente protegido

## Contrato y estado

**Implementado en la rama `28-bkl-028-carrito-persistente-crud` (PR #83); no forma parte de `dev` hasta completar revisión y merge.**

La API usa los **tokens Bearer opacos y revocables** de BKL-016 y el catálogo JDBC de BKL-018. Las cinco operaciones exigen un usuario autenticado con rol interno `CLIENTE` (autoridad `ROLE_CLIENT`). El rol ADMIN **no** puede operar el carrito de un cliente; responde `403`. Un visitante o token revocado recibe `401`.

| Método | Ruta | Resultado |
| --- | --- | --- |
| `GET` | `/api/cart` | `200 Cart` (incluso vacío) |
| `POST` | `/api/cart/items` | `200 Cart`; suma cantidad si el artículo ya está |
| `PATCH` | `/api/cart/items/{productId}` | `200 Cart`; reemplaza cantidad absoluta |
| `DELETE` | `/api/cart/items/{productId}` | `204` (idempotente) |
| `DELETE` | `/api/cart` | `204` (vacía el carrito; idempotente) |

JSON de alta: `{"productId": 1, "quantity": 2}`. Para PATCH: `{"quantity": 3}`. **Nunca** se aceptan `userId`, `cartId`, `priceCrc`, `role` u otros atributos del cliente. La identidad se obtiene exclusivamente de `SessionPrincipal.id()`, que Spring Security establece a partir del Bearer.

## Respuesta de ejemplo

```json
{
  "items": [
    {
      "productId": 1,
      "sku": "TEC-001",
      "name": "Mouse",
      "quantity": 2,
      "stock": 8,
      "available": true,
      "unitPriceCrc": 12990.00,
      "lineSubtotalCrc": 25980.00
    }
  ],
  "totalItems": 2,
  "subtotalCrc": 25980.00,
  "checkoutReady": true
}
```

`totalItems` suma cantidades, no líneas. `subtotalCrc` se calcula en el backend a partir de **precios actuales** de PostgreSQL y no incluye envío ni impuestos. **No es el total final de compra**; ningún precio que envíe el navegador se acepta como fuente de verdad.

El carrito no reserva stock. Si un producto se desactiva o su stock disminuye después de añadirlo, la línea sigue visible con `available: false`, y `checkoutReady` pasa a `false`, para permitir retirar o corregir productos. El subtotal sigue reflejando los precios vigentes de **todas** las líneas, incluidas las no disponibles; es únicamente informativo. El checkout BKL-033 debe **revalidar y recalcular** precios, stock, impuestos y envío bajo su propia transacción.

## Persistencia

Migración `V5__create_carts.sql` posterior a V1–V4, sin modificarlas:

- `carts(id, user_id UNIQUE, created_at)`: carrito único por cuenta, FK `users` con `ON DELETE CASCADE`.
- `cart_items(id, cart_id, product_id, quantity, timestamps)`: FKs a carrito/producto, `UNIQUE(cart_id, product_id)` y `CHECK(quantity BETWEEN 1 AND 99)`.
- La consulta `findItems` utiliza `carts.user_id = ?` y lee `products.price_crc`, `products.active` y `products.stock` actuales.
- Alta/incremento: `INSERT ... ON CONFLICT DO UPDATE` atómico, con condición de stock y límite de 99 unidades en el mismo SQL. Dos POST simultáneos sobre una línea no causan pérdida silenciosa de actualizaciones.
- El PATCH actualiza la cantidad absoluta si el artículo existe, sigue activo y tiene stock suficiente; `DELETE` permite quitar también líneas cuyo producto fue desactivado.
- Las escrituras llevan `@Transactional`. El carrito permanece asociado a la cuenta tras cerrar sesión y abrir otra sesión; una sesión revocada no puede consultarlo.

**Las restricciones del carrito no sustituyen una reserva de inventario** ni previenen por sí solas sobreventa entre pedidos. Esa garantía pertenece a BKL-027/BKL-033.

## Errores

| Respuesta | Causa |
| --- | --- |
| `400 application/problem+json` | JSON no válido, campos extra, tipos no enteros, cantidades fuera de 1–99 o identificadores no positivos |
| `401 application/problem+json` | Sin Bearer válido, expirado o revocado |
| `403 application/problem+json` | Usuario autenticado sin rol CLIENT |
| `404 application/problem+json` | Producto inexistente/inactivo al añadir o línea de carrito ajeno/inexistente al editar |
| `409 application/problem+json` | Stock insuficiente, cantidad acumulada superior al máximo o cambio concurrente |

Errores con `code` estable, sin consultas SQL ni datos sensibles.

## Principios de diseño

```text
BearerTokenAuthenticationFilter
          |
SessionPrincipal (usuario propietario)
          |
CartController (HTTP, DTO y códigos de estado)
          |
CartService (reglas de cantidad y cálculo de subtotal)
          |                         |
CartRepository (interfaz)     ProductRepository (interfaz)
          |                         |
JdbcCartRepository         JdbcProductRepository
          \_________________________/
                  PostgreSQL
```

- **SOLID/SRP**: HTTP, reglas de negocio, persistencia y seguridad están separados.
- **SOLID/DIP**: `CartService` depende de contratos `CartRepository` y `ProductRepository`, no de JDBC.
- **GRASP Controller**: el adaptador HTTP delega el caso de uso.
- **GRASP Information Expert**: el servicio calcula montos a partir de datos de dominio; la base protege unicidad y stock al modificar.
- **GRASP Low Coupling / High Cohesion**: cada módulo controla su comportamiento y las referencias a catálogo se realizan por contrato.
- **MVC**: controlador backend REST; modelo de dominio; futura vista Next.js (BKL-029). Esta API por sí sola no completa el flujo MVC visible.
- **GoF**: Strategy (envío), Facade (checkout) y Observer (pedido) siguen pendientes y **no** se atribuyen a este módulo.

## Pruebas reproducibles

El pipeline de CI usa PostgreSQL 17 efímero en `techstore_test`, aplica Flyway V1–V5, ejecuta `mvn clean verify` y prueba la API en funcionamiento con `scripts/ci/smoke-cart.py`.

```powershell
git fetch origin
git switch --track origin/28-bkl-028-carrito-persistente-crud
mvn -f apps/api/pom.xml clean test
# Con PostgreSQL techstore_test y DB_TEST_PASSWORD configurados:
mvn -f apps/api/pom.xml clean verify
npm run validate:api-contract
npm run test:api-contract
```

Las pruebas `CartServiceTest` y `CartIT` cubren cálculos, cantidades, stock, actualización, aislamiento de propietarios, rol, errores y persistencia. Los tests JDBC verifican explícitamente `current_database() = techstore_test` antes de escribir y hacen rollback.

## Trabajo posterior

- BKL-029: página de carrito editable en Next.js, estados accesibles, autenticación del frontend.
- BKL-030: snapshots históricos de líneas de pedido.
- BKL-032: impuestos/envío y total definitivo **solo del lado servidor**; Strategy para costos de envío.
- BKL-033: creación transaccional del pedido y reserva real de stock.
- BKL-067/068: Facade/Observer en el checkout/pedidos.

El carrito actual es un prototipo para desarrollo local, no un checkout productivo.
