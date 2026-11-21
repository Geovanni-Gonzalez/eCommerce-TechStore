# BKL-043 — Interfaz de historial, detalle y estado de pedidos

## Objetivo y dependencias

La página **`/pedidos`** presenta el historial del cliente usando el backend **BKL-042**, sin modificar estados ni inventario. Usa las respuestas de:

- `GET /api/orders?limit=12[&beforeId=...]` (resúmenes propios, cursor)
- `GET /api/orders/{id}` (detalles comerciales históricos del propietario)
- `GET /api/orders/{id}/history` (timeline sin IDs de actores)

Estas rutas requieren **Bearer de ROLE_CLIENT**, filtran `user_id` en PostgreSQL y devuelven el mismo 404 para un pedido ajeno o inexistente. No se usan rutas ADMIN ni se envían `userId`, montos o estados elegidos desde el navegador. No se agregó ningún endpoint: OpenAPI sigue en **31 operaciones**.

## Experiencia del cliente

1. La navegación principal incluye **Mis pedidos**. Sin autenticación solo se muestra un formulario de acceso privado, nunca detalles precargados.
2. Hasta implementar el login web general BKL-017, la página usa un **login puntual** vía `POST /api/auth/login`: valida `user.role=CLIENT`; el token Bearer opaco vive solo en un `useRef` de React, sin cookies, URL, localStorage ni sessionStorage. Contraseña y correo se limpian después de consultar. La API debe usar HTTPS, excepto HTTP en localhost/loopback durante el desarrollo (validador BKL-039).
3. Se muestra una página de **12 pedidos** ordenados por ID descendente. `Cargar pedidos anteriores` utiliza exclusivamente el cursor `nextBeforeId` de BKL-042 y evita duplicados; sin pedidos aparece un estado vacío. `Actualizar` vuelve a consultar el backend.
4. Seleccionar un pedido consulta **detalle e historial** de forma conjunta. Ante error HTTP 404, datos inconsistentes o fallo de red, no se muestra el detalle anterior; hay reintento explícito. Los IDs de pedidos se toman solo de la lista validada.
5. El detalle muestra producto y precio históricos, cantidad, líneas en CRC, subtotal, envío, impuestos, total, dirección de entrega registrada y timeline de estados/motivos/actores sin IDs internos. Los nombres y precios provienen de `order_items`, no del catálogo actual.
6. **Cerrar sesión** borra inmediatamente la vista privada y el token en memoria, invalida las respuestas asincrónicas en vuelo mediante un contador de generación y envía `POST /api/auth/logout` para revocar la sesión cuando el backend está disponible.

### Texto y significado de estados

| Estado | Texto visible | Regla |
|---|---|---|
| `PENDING` | **Pendiente** | El pedido no se ha confirmado; no deducir pago |
| `CONFIRMED` | **Confirmado (sandbox)** | Confirmación de simulación, **no** prueba de dinero bancario |
| `CANCELLED` | **Cancelado** | No implica devolución bancaria |

El historial usa los motivos de BKL-045/BKL-040: `CREATED`, `BASELINE`, `CLIENT_CANCEL`, `ADMIN_CANCEL`, `EXPIRED`, `SANDBOX_VERIFIED`; actores `CLIENT`, `ADMIN`, `SYSTEM`. Las fechas se muestran explícitamente en zona `America/Costa_Rica`; los importes en colones `CRC`. El diseño usa componentes HTML semánticos (lista, tabla, `dl`, `ol`, `address`), mensajes accesibles `role=status/alert`, etiquetas visibles, navegación por teclado y estilos responsivos.

## Seguridad y manejo de fallos

- El sitio no cachea los GET (`cache:"no-store"`, `credentials:"omit"`, `referrerPolicy:"no-referrer"`). No hay datos privados en HTML inicial sin autenticar, ni en props de servidor.
- `isSafeApiOrigin` bloquea credenciales/tokens enviados a URLs de API inseguras (HTTP remoto, querystrings o usuario/password).
- La lista remueve cualquier campo no permitido del JSON entrante. La pantalla valida identificadores enteros positivos, estados conocidos, modo `SIMULATED`, dinero no negativo, fechas, página en orden estricto y `nextBeforeId` consistente.
- El detalle valida que su `id` coincida con el seleccionado; el historial únicamente admite estados, motivos y actores conocidos, **sin copiar** `actorId` o ID de auditoría a React.
- Cuando cambian pedido o sesión, una solicitud antigua no puede reintroducir datos de otra selección o usuario: se comprueban `generation`, `detailRequest` y el Bearer actual antes de actualizar el estado.
- Ante 401 o 403 se elimina la sesión local y toda vista privada; el usuario debe volver a autenticarse. Ante 404 se informa genéricamente **inexistente o ajeno** sin diferenciarlo.
- Los campos de dirección y teléfono aparecen únicamente en el panel de detalle, después de confirmar propietario vía API BKL-042.
- El resultado de BKL-039 enlaza a **Mis pedidos**, pero no transmite el token entre páginas ni habilita sesión persistente. Es una limitación consciente pendiente de unificar bajo BKL-017.

## Validación

`apps/web/src/lib/order-history.test.mjs` ejecuta **9 pruebas de dominio/seguridad**: estados/mensajes, paginación por cursor, datos mal formados, snapshots, propiedad del detalle, ausencia de IDs internos, motivos históricos, fechas y dinero. El workflow Next.js añade `node --experimental-strip-types --test` además de ESLint, TypeScript, OpenAPI, build y smoke HTTP. El smoke abre `/pedidos`, verifica que la entrada privada esté presente y que el HTML inicial no revele direcciones.

La suite backend PostgreSQL existente de **232 pruebas** cubre los filtros ROLE_CLIENT / ROLE_ADMIN, IDOR y snapshots reales. Las pruebas de UI puras no reemplazan un test E2E en navegador con interacción y backend arrancado; este último sigue como trabajo BKL-044.

**Sin cobro bancario real.** La interfaz es una herramienta de consulta para el prototipo SANDBOX.
