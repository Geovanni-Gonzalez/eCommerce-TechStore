# BKL-046 — Panel administrativo de pedidos e inventario

Issue: https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/47

## Acceso y composición

`/admin` verifica el rol `ADMIN` contra `GET /api/admin/check` antes
de montar los paneles. Incluye navegación entre **Pedidos**, **Inventario**
y **Catálogo** (BKL-024). La sesión y el Bearer opaco BKL-017 permanecen
exclusivamente en memoria; nunca se guardan en cookies, URLs, localStorage
o sessionStorage. La API `/api/admin/**` también comprueba ROLE_ADMIN,
por lo que ocultar botones no es el control de autorización.

Los paneles montan sus propias consultas al seleccionarse y se desmontan al
cambiar de sección. Incluyen estados de carga, errores, confirmaciones y
paginación. No agregan APIs ni modificaciones del backend: reutilizan BKL-045
y BKL-026, que están integradas en `dev`.

## Pedidos

- `GET /api/admin/orders?limit=20&beforeId=...`: listado con cursor
  descendente, ID, usuario propietario, fecha, estado y total CRC.
- `GET /api/admin/orders/{id}`: detalle histórico e ítems, sin
  exponer contraseñas ni métodos de pago.
- `GET /api/admin/orders/{id}/history`: transiciones auditadas con
  actor, motivo y fecha.
- `PATCH /api/admin/orders/{id}/status` solo con
  `{ "status": "CANCELLED" }`: disponible exclusivamente para pedidos
  `PENDING`, tras confirmación explícita. La API decide y valida el
  cambio y la liberación transaccional de reservas. Un 409 indica que
  ya no es posible cancelarlo.
- Los estados `CONFIRMED` y `CANCELLED` se muestran sin controles de
  cambio. **Ningún administrador puede forzar CONFIRMED**: depende de la
  conciliación verificable de pagos BKL-040.
- En caso de respuesta ambigua no se reintenta la mutación automáticamente;
  se debe consultar el historial.

## Inventario

- `GET /api/admin/inventory?page=0&size=20`: stock físico, reservado y
  disponible; el saldo físico es una proyección, no otro contador editable.
- `GET /api/admin/inventory/products/{productId}`: balance del artículo.
- `GET /api/admin/inventory/products/{productId}/movements?page=0&size=20`:
  bitácora auditable con deltas y referencias de pedido/actor.
- `POST /api/admin/inventory/products/{productId}/adjustments`: permite
  `ADJUSTMENT_IN` o `ADJUSTMENT_OUT`, cantidad entera 1..1 000 000,
  motivo obligatorio de 5 a 300 caracteres y `requestId` UUID.
  La API realiza un ajuste atómico con `SELECT FOR UPDATE`, no altera
  unidades reservadas y registra el motivo y la identidad ADMIN.
- Cada intento genera un UUID único al confirmarse la decisión. Si la
  petición falla o se pierde su respuesta, el formulario conserva ese
  UUID mientras no cambien los campos. Un reintento idéntico es
  idempotente según BKL-026. Cambiar campos crea una solicitud nueva.
- El historial y el saldo se recargan tras un resultado confirmado; ante
  error, se recomienda revisar movimientos antes de repetir.

## Catálogo

Permanece integrado el gestor BKL-024 para crear, editar y desactivar
productos y administrar categorías. El stock sigue siendo de **solo
lectura** en el CRUD del catálogo, evitando eludir movimientos
transaccionales de inventario.

## Limitaciones y QA final

- No se incluyen cobros bancarios reales, edición manual de reservas,
  cambio de estado CONFIRMED ni controles para alterar datos de cliente.
- La lista de inventario utiliza identificadores de producto; los
  nombres pueden consultarse desde la sección Catálogo.
- El Bearer y los formularios de administración se pierden al recargar;
  es una propiedad de la sesión en memoria BKL-017.
- Las pruebas integrales y visuales de BKL-046 quedan para el final
  del producto, conforme a la decisión del equipo. La CI preexistente
  no se deshabilita.
- QA final: validar ADMIN/CLIENT/visitante; cancelación PENDING y 409
  concurrente; paginación; ajustes positivos/negativos; stock insuficiente;
  auditoría, requests repetidos y pérdida de respuesta; accesibilidad
  por teclado y diseño móvil.
