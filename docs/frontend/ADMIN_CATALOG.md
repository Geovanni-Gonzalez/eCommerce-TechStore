# BKL-023 y BKL-024 — CRUD ADMIN y panel del catálogo

Esta implementación está **apilada sobre la PR #105 (BKL-020/021/022)** porque
reutiliza `brand` (Flyway V16) y la categoría pública de productos.

## Seguridad y contratos

Las 8 operaciones requieren `Authorization: Bearer <token>` con rol **ADMIN**
verificado en el backend por `ApiSecurityConfig`; una cuenta CLIENT recibe
403 y una no autenticada 401. No se usa ningún Bearer en cookies, URLs,
`localStorage` o `sessionStorage`. La API ADMIN responde con `Cache-Control:
no-store` en los endpoints implementados.

| Operación | Endpoint |
| --- | --- |
| Listar todos los productos (activos e inactivos) | `GET /api/admin/catalog/products?page=0&size=20` |
| Crear producto | `POST /api/admin/catalog/products` |
| Editar producto | `PUT /api/admin/catalog/products/{id}` |
| Activar/desactivar (reversible) | `PATCH /api/admin/catalog/products/{id}/status` |
| Listar categorías | `GET /api/admin/catalog/categories` |
| Crear categoría | `POST /api/admin/catalog/categories` |
| Renombrar categoría | `PUT /api/admin/catalog/categories/{id}` |
| Eliminar categoría sin productos | `DELETE /api/admin/catalog/categories/{id}` |

POST/PUT productos aceptan **exactamente** estos campos: `sku, name,
description, categoryId, brand, priceCrc`. Se validan SKU único, longitud
de nombre, marca, precio CRC hasta dos decimales, y categoría existente.
El precio y los campos se guardan con SQL parametrizado dentro de un caso
de uso transaccional. La creación comienza con **stock=0, active=true**.

El stock es **solo lectura** en el panel: no se modifica mediante estos
endpoints, porque los pedidos pueden contener reservas vigentes y las
operaciones de inventario requieren movimientos/auditoría específicos
(BKL-026/BKL-046). Los productos se **desactivan**, no se eliminan
físicamente, protegiendo los snapshots y vínculos del sistema.

Al eliminar una categoría usada por cualquier producto, la FK `RESTRICT`
de PostgreSQL devuelve conflicto HTTP 409. Los cuerpos inválidos, claves
desconocidas o campos no permitidos devuelven HTTP 400. Los errores
administrativos tienen formato RFC Problem Details sin detalles SQL.

## Pantalla web

`/admin` solo monta el panel tras verificar `GET /api/admin/check`.
El administrador puede crear/editar productos, activarlos/desactivarlos
con confirmación explícita y administrar categorías. La tabla informa
stock sin ofrecer ajustes directos. El paginado lista también productos
desactivados. Los mensajes de confirmación y error son visibles y
el diseño es adaptable al tamaño de pantalla.

## Dependencias y pendientes

- PR #105 / Flyway V16 debe integrarse **antes** de esta PR. Hasta entonces
  utilizar su rama como base de revisión.
- BKL-026/046 añadirán controles de inventario y pedidos sin reutilizar el
  CRUD para alterar stock.
- Conforme a la indicación del equipo, las pruebas exploratorias, de
  integración y E2E de este bloque se efectuarán en la fase final.
  No se desactiva la CI automática del repositorio.
- Antes del merge se deben inspeccionar eventuales conflictos y comprobar
  las reglas de permisos, las migraciones y el flujo administrativo.
