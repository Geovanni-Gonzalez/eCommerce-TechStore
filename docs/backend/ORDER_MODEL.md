# BKL-030 — Pedido, DetallePedido y snapshots comerciales

**Estado:** Implementación del modelo y del repositorio en la rama `30-bkl-030-pedidos-snapshots-comerciales`. **No hay endpoint de creación de pedidos todavía**; el contrato OpenAPI de `POST /api/orders`, `GET /api/orders` y `GET /api/orders/{id}` permanece correctamente como `planned`.

## Necesidad y alcance

El carrito BKL-028 guarda cantidades y muestra precios **actuales** del catálogo; un pedido debe preservar de forma histórica lo acordado al confirmar la compra. BKL-030 crea esa representación duradera y su persistencia, sin exponer rutas HTTP prematuras ni reservar inventario. El flujo que creará un pedido desde un cliente autenticado deberá implementarse en BKL-032/BKL-033 usando el catálogo, el carrito y la lógica de checkout de confianza.

El issue de revisión ERD general **BKL-007 sigue abierto**. Este esquema V6 cubre estrictamente las entidades `Pedido` y `DetallePedido`; se considera un primer diseño compatible con el contrato OpenAPI, pendiente de la revisión integral de reservas/inventario/pagos. No se afirma que cumpla toda la definición futura del ERD.

## Decisiones de diseño (ADR simplificado)

| Decisión | Alternativa descartada | Justificación y coste |
| --- | --- | --- |
| **Snapshot de líneas** (`product_id`, `product_name`, `quantity`, `unit_price_crc`, `line_total_crc`) | Leer precios y nombres actuales del catálogo al consultar pedidos | Un cambio o eliminación del producto no reescribe el historial. Se duplican deliberadamente algunos datos por integridad histórica. |
| **Snapshot de dirección** (provincia, cantón, distrito, señas, destinatario, teléfono) | FK a una dirección de perfil que pueda editarse | La entrega histórica no cambia si el perfil cambia; se exige proteger esos datos personales en futuros endpoints. |
| **BigDecimal / NUMERIC en CRC** con 2 decimales | `float`/`double` para dinero | Evita redondeo binario; se rechazan valores con fracciones adicionales o desbordamientos en vez de redondearlos. |
| **Estado inicial PENDING**; conjunto PENDING/CONFIRMED/CANCELLED | Permitir estados arbitrarios mediante texto libre | Fija el vocabulario existente en OpenAPI. La máquina de transiciones y auditoría queda para BKL-045. |
| **payment_mode = SIMULATED** | Conexión a pasarela o uso de tarjetas | Prototipo académico sin dinero real; SQL impide etiquetar un pedido como pago real. |
| **Cabecera e ítems en una transacción** | Escribir primero pedido y aceptar detalles parciales | `@Transactional` protege contra pedidos incompletos ante fallos SQL. Aún falta reservar stock en la transacción de checkout. |
| **Lectura filtrada por propietario** (`findByIdForUser`) | Buscar por ID sin el usuario | Previene exposición cruzada en el repositorio. La autorización HTTP de pedidos será parte de sus endpoints futuros. |
| **Identificador histórico de producto sin FK en ítems** | FK al catálogo `products` con RESTRICT o CASCADE | Se conserva el `product_id` original aunque el producto sea eliminado, y el snapshot queda autónomo. El checkout debe comprobar la existencia del producto antes de crear el borrador. |
| **Sin controller ni creación HTTP en BKL-030** | Exponer `POST /api/orders` antes del checkout | Evita aceptar precios inventados, omitir stock o permitir compras inconsistentes. La verificación y cálculo definitivo deben preceder a la persistencia. |

## Esquema de persistencia

```text
users 1 ──── N orders (V6)
                 │  id, user_id, status, payment_mode
                 │  ship_* [dirección copiada]
                 │  subtotal_crc, shipping_crc, tax_crc, total_crc
                 │  created_at, updated_at
                 │
                 1
                 │
                 N
             order_items (V6)
               id, order_id, line_number
               product_id (identificador histórico)
               product_name, quantity
               unit_price_crc, line_total_crc
```

**Flyway `V6__create_order_snapshots.sql`** deja V1–V5 intactas. Introduce claves primarias, FK `orders.user_id -> users.id` con `ON DELETE RESTRICT`, FK de detalle a cabecera con `ON DELETE RESTRICT`, índice de órdenes por usuario/fecha, orden de líneas estable y restricciones:

- `orders.status` acepta solamente `PENDING`, `CONFIRMED`, `CANCELLED`.
- `orders.payment_mode` acepta solamente `SIMULATED`.
- `orders.total_crc = subtotal_crc + shipping_crc + tax_crc`.
- Cantidades de 1 a 99, precios no negativos y `line_total_crc = unit_price_crc * quantity`.
- `UNIQUE(order_id, product_id)` y `UNIQUE(order_id, line_number)` impiden duplicar líneas.
- Se exige dirección completa, con longitudes compatibles con el contrato `ShippingAddress`; valores sin caracteres de control.

**Limitación explícita:** las restricciones SQL no comparan el subtotal de la cabecera con la suma de todas las líneas; `OrderDraft` comprueba esa invariante antes de persistir y el repositorio debe invocarse exclusivamente desde un servicio de checkout de confianza. Nuevas rutas de escritura no deben saltarse ese caso de uso.

## Modelo Java y responsabilidades

```text
OrderDraft (dirección, items, importes validados)
  ├── ShippingAddress
  ├── List<OrderItemSnapshot>
  └── OrderTotals
           |
           v
   OrderRepository (puerto)
           |
           v
  JdbcOrderRepository (@Transactional)
           |
           v
       PostgreSQL V6
           |
           v
 Order (PENDING/SIMULATED + snapshots y timestamp)
```

- `CrcMoney` valida no negatividad, precisión NUMERIC y escala de 2 decimales con `RoundingMode.UNNECESSARY`.
- `OrderItemSnapshot` normaliza el nombre y calcula el total de línea sin datos del catálogo al leer el pedido.
- `OrderTotals` verifica la aritmética de los importes.
- `OrderDraft` exige usuario, dirección, líneas no vacías, productos no repetidos y subtotal exacto; copia defensivamente la lista para que sea inmutable.
- `JdbcOrderRepository.saveDraft(draft)` crea cabecera e ítems atómicamente, siempre en estado inicial `PENDING` y con `SIMULATED`; `findByIdForUser(userId,orderId)` solo devuelve pedidos del propietario.
- `Order` representa el agregado recuperado y valida nuevamente sus invariantes.

### SOLID, GRASP, MVC y GoF

**SRP:** validación de valores/dominio separada del adaptador SQL. **DIP:** los futuros controladores/fachadas dependerán del puerto `OrderRepository`, no de `JdbcTemplate`. **GRASP Information Expert:** los objetos del dominio validan sus propias invariantes; **Low Coupling/High Cohesion:** las consultas del pedido no requieren volver al módulo catálogo.

**MVC:** todavía no hay vista ni controlador HTTP de pedidos; se implementarán después. **GoF:** Strategy (envíos), Facade (checkout) y Observer (evento de pedido) siguen pendientes; no deben darse por demostrados por esta migración.

## Pruebas

`OrderDomainTest` valida dirección, snapshots, importes, validaciones, duplicados y listas inmutables.

`OrderRepositoryIT` inicia Spring con PostgreSQL real aislado (`techstore_test`), aplica Flyway V6 y usa transacciones con rollback. Verifica cabecera+detalles, snapshot intacto tras editar/eliminar un producto, restricción por usuario, estado/pago válidos, integridad de importes y FK de usuario.

```powershell
git fetch origin
git switch --track origin/30-bkl-030-pedidos-snapshots-comerciales
mvn -f apps/api/pom.xml clean test
# Para probar V6 y JDBC contra techstore_test:
mvn -f apps/api/pom.xml clean verify
```

CI ejecuta `mvn clean verify` sobre PostgreSQL 17 efímero junto con regresión de carrito, catálogo, registro y autenticación. No se añade smoke de creación de pedido, porque el endpoint aún no existe y sería inseguro habilitarlo sin sus condiciones de negocio.

## Siguientes etapas

- **BKL-007:** revisar ERD completo (inventario, reservas, intentos de pago y eventos).
- **BKL-032:** calcular impuestos, envío (Strategy) y total final desde la base, no desde JSON.
- **BKL-027 + BKL-033:** validar stock y crear pedido/reserva en la misma transacción; incorporar Facade.
- **BKL-068:** emitir evento de pedido creado con Observer y evidencia auditada.
- **BKL-042:** lectura de historial con autorización HTTP, paginación y prevención de IDOR.
- **BKL-045:** transiciones de estado válidas y auditables.

**No hay procesador de pagos reales, no se capturan tarjetas, y ninguna compra se considera confirmada en esta etapa.**
