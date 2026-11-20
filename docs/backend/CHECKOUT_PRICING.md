# BKL-032 — Cálculo de importes de checkout y GoF Strategy

## Estado

Implementado en la rama `32-bkl-032-totales-envio-strategy`, propuesta para integrar a `dev` mediante PR; no supone creación de pedidos ni reserva de inventario.

**BKL-030** ya modela `OrderTotals` (BigDecimal, importes CRC) y snapshots de `Order`. **BKL-028** guarda el carrito y lee precios de catálogo. Este módulo incorpora el cálculo del subtotal, envío, impuesto y total usando exclusivamente las líneas actuales del carrito **del usuario autenticado**.

## API de cotización

`GET /api/checkout/quote`

- Requiere token Bearer opaco válido y **rol CLIENT**. No acepta body, `userId`, `priceCrc`, `totalCrc` ni una política de envío enviada por el navegador.
- La identidad se obtiene desde `SessionPrincipal.id()` de Spring Security.
- Recupera `CartLine` mediante `CartRepository.findItems(userId)`, con precio, stock y estado **actuales** obtenidos de PostgreSQL.
- Valida carrito no vacío, productos activos, cantidades positivas (1–99) y disponibilidad suficiente.
- Responde `200 application/json` con `Cache-Control: no-store`, desglose CRC y `estimateOnly: true`.

Ejemplo conceptual para una línea de ₡10 000, envío fijo ₡2 500 y tasa ilustrativa 13 %:

```json
{
  "totalItems": 1,
  "totals": {
    "subtotalCrc": 10000.00,
    "shippingCrc": 2500.00,
    "taxCrc": 1300.00,
    "totalCrc": 13800.00
  },
  "shippingPolicy": "FIXED",
  "estimateOnly": true
}
```

`shippingPolicy` informa la política seleccionada por el **servidor**, no constituye una opción arbitraria elegida por el cliente en esta etapa.

| HTTP | Código estable | Situación |
| --- | --- | --- |
| 200 | — | Cotización correcta con valores calculados por servidor |
| 401 | `AUTHENTICATION_REQUIRED` | Token Bearer ausente, inválido o revocado |
| 403 | `ACCESS_DENIED` | Usuario sin rol CLIENT |
| 409 | `CART_UNAVAILABLE` | Hay productos inactivos o sin suficientes existencias |
| 422 | `EMPTY_CART` | Carrito vacío |

Los errores usan `application/problem+json`. Ni contraseñas, tokens ni SQL se imprimen en la respuesta.

## Fórmulas y alcance

```text
subtotalCrc = Σ (precio_actual_catalogo × cantidad)
shippingCrc = shippingStrategy.calculateShippingCrc(subtotalCrc)
taxCrc      = round_HALF_UP(subtotalCrc × taxRate, 2)
totalCrc    = subtotalCrc + shippingCrc + taxCrc
```

Valores internos: `BigDecimal`, escala 2 CRC, controles de capacidad `NUMERIC(12,2)` en precios y `NUMERIC(14,2)` en totales, con `CrcMoney` y `OrderTotals` de BKL-030. El impuesto **no aplica sobre el envío en este prototipo**. Se permite configurar una tasa entre 0 y 1, con hasta cuatro decimales.

**Importante:** el valor predeterminado 13 % es una **suposición ilustrativa de proyecto**, no una determinación de tratamiento fiscal para todas las categorías de producto ni una factura tributaria. En producción se debe revisar jurisdicción, exenciones, base imponible, reglas de redondeo y facturación con un profesional competente.

La cotización es **provisional**: no bloquea inventario, no persiste un pedido, no procesa pagos, no mantiene el precio ante cambios de catálogo y no calcula tarifas dependientes de dirección. BKL-031 y BKL-033 deberán validar dirección y calcular importes otra vez al confirmar el pedido **dentro de la misma transacción de reserva**. Esto evita cobrar sobre una cotización desactualizada.

## Patrón GoF Strategy — Diseño ejecutable

```text
CheckoutQuoteController (HTTP, rol CLIENT)
        │
CheckoutQuoteService (cotizar al propietario)
        │
        ├── CartRepository.findItems(userId) ── PostgreSQL (catálogo vigente)
        │
        └── CheckoutTotalsCalculator (contexto GoF Strategy)
                 │
                 └── ShippingStrategy [interfaz]
                       ├── FixedShippingStrategy
                       │     tarifa fija
                       └── FreeOverThresholdShippingStrategy
                             gratis desde un subtotal mínimo
```

- **Contexto:** `CheckoutTotalsCalculator` invoca `ShippingStrategy.calculateShippingCrc(subtotal)`, sin conocer la clase concreta.
- **Interfaz:** `ShippingStrategy` con contrato de política y método para calcular costo.
- **ConcreteStrategy A:** `FixedShippingStrategy`, siempre cobra la tarifa configurada.
- **ConcreteStrategy B:** `FreeOverThresholdShippingStrategy`, cobra tarifa por debajo del umbral y cero al alcanzarlo.
- **Selección/intercambio:** `CheckoutPricingConfig.selectPolicy(...)` inyecta una u otra implementación a través de Spring. Las pruebas ejecutan **ambas** con los mismos datos y obtienen resultados diferentes sin modificar el calculador.

Esto es un patrón **GoF Strategy real**, no solo una interfaz o inyección genérica: el comportamiento intercambiado es una política de envío, cambia el resultado de cálculo y está cubierto por tests.

### Configuración

`apps/api/src/main/resources/application.yml` ofrece defaults demostrativos configurables mediante variables de entorno:

| Variable | Predeterminado | Uso |
| --- | --- | --- |
| `CHECKOUT_SHIPPING_POLICY` | `FREE_OVER_THRESHOLD` | `FIXED` o `FREE_OVER_THRESHOLD` |
| `CHECKOUT_SHIPPING_FEE_CRC` | `2500.00` | Tarifa fija base en CRC |
| `CHECKOUT_FREE_SHIPPING_THRESHOLD_CRC` | `30000.00` | Subtotal desde el que el envío es gratuito |
| `CHECKOUT_TAX_RATE` | `0.13` | Tasa ilustrativa (13 %) sobre subtotal |

Ejemplo de PowerShell **antes de arrancar la API**:

```powershell
$env:CHECKOUT_SHIPPING_POLICY = "FIXED"
$env:CHECKOUT_SHIPPING_FEE_CRC = "2000.00"
$env:CHECKOUT_FREE_SHIPPING_THRESHOLD_CRC = "30000.00"
$env:CHECKOUT_TAX_RATE = "0.13"
mvn -f apps/api/pom.xml spring-boot:run
```

Reiniciar la API para cambiar la estrategia. Una política desconocida o una tasa inválida debe fallar en configuración/cálculo; no se cambia automáticamente a una política arbitraria.

## Principios y decisiones

- **SOLID SRP:** el controller expone HTTP, el servicio identifica al usuario y consulta el carrito, el calculador realiza aritmética y las Strategies implementan envío.
- **SOLID OCP:** agregar otra modalidad de envío implica añadir una implementación `ShippingStrategy` y su selección de configuración, sin reescribir el calculador.
- **SOLID DIP:** calculador depende de `ShippingStrategy`, no de una clase fija; servicio depende de `CartRepository`.
- **GRASP Controller/Experto/Bajo acoplamiento/Alta cohesión:** decisiones de negocio fuera del controlador y montos propiedad del calculador.
- **MVC:** el controlador REST devuelve el modelo de cotización; la pantalla Next.js que lo consume debe implementarse en el flujo UI posterior.
- **GoF:** Strategy **implementado**; Facade y Observer siguen pendientes en checkout y eventos.

## Pruebas y verificación

`CheckoutTotalsCalculatorTest` prueba dos estrategias, umbral exacto, redondeo HALF_UP, base imponible, montos inválidos, desbordamientos y disponibilidad. `CheckoutQuoteServiceTest` verifica que se consulte el carrito del usuario autenticado y que no se acepten importes arbitrarios.

`CheckoutQuoteIT` comprueba la API completa contra PostgreSQL/Flyway y Spring Security reales: 401/403/409/422, rol CLIENT, doble usuario, precio actualizado, importe calculado y revocación de sesión.

`scripts/ci/smoke-quote.py` solicita una cotización HTTP contra el JAR y la base efímera de GitHub Actions. OpenAPI incorpora `GET /api/checkout/quote` y `CheckoutQuote` con `x-implementation-status: implemented`.

```powershell
git fetch origin
git switch --track origin/32-bkl-032-totales-envio-strategy
mvn -f apps/api/pom.xml clean test
# Con PostgreSQL techstore_test y DB_TEST_PASSWORD configurados:
mvn -f apps/api/pom.xml clean verify
npm run validate:api-contract
npm run test:api-contract
```

**Fuera de este BKL:** BKL-033 (persistir pedido real/reserva transaccional), BKL-031 (dirección de envío), BKL-067 (Facade) y BKL-068 (Observer). Ningún pedido se considera confirmado por obtener una cotización.
