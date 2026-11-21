# BKL-029 — Interfaz del carrito persistente

## Alcance
- `/productos` y `/productos/{id}` incluyen **Añadir al carrito** con `POST /api/cart/items`.
- `/carrito` lista líneas con `GET /api/cart`, sustituye cantidades mediante `PATCH /api/cart/items/{productId}`, elimina con `DELETE /api/cart/items/{productId}` y vacía mediante `DELETE /api/cart` con confirmación de usuario.
- Solo cuentas `CLIENT` autenticadas con Bearer opaco en **memoria** por el `AuthProvider` de BKL-017 pueden consultar o mutar carrito. Invitados ven enlace a `/login`; ADMIN no tiene acceso al carrito. El backend exige el rol y determina el propietario por token.
- Estado de carga, vacíos, errores de red, caducidad de sesión, falta de stock, cambios simultáneos, controles accesibles y diseño adaptable.
- **El subtotal y precios vienen exclusivamente de la API**. El cliente nunca manda `userId`, precios ni `cartId` en mutaciones; solo `productId` y/o `quantity`. No se reserva stock hasta que se confirme un pedido.
- El carrito permanece persistido en PostgreSQL entre sesiones; el Bearer no se guarda en `localStorage`, `sessionStorage`, URL ni cookies.

## Alcance pendiente
BKL-031 añadirá dirección de entrega y BKL-034 el checkout visual. Se muestra subtotal **estimado** sin impuestos ni envío; no se ofrece acción ficticia de pagar. El historial BKL-043 mantiene temporalmente su sesión local y no forma parte de este flujo.

## Validación local
1. Levantar PostgreSQL y Spring Boot, aplicar semilla `scripts/dev/seed-catalog.sql`.
2. Abrir `http://localhost:3000/registro`, crear cliente, abrir `/login` e iniciar sesión.
3. Abrir `/productos`, entrar a mouse y pulsar **Añadir al carrito**. Debe navegar a `/carrito`, mostrando línea y subtotal API.
4. Cambiar cantidad a 2, pulsar **Actualizar Mouse inalámbrico Demo**. Comprobar importe y total desde la API. Quitar línea y probar carrito vacío. Probar también **Vaciar carrito** con confirmación.
5. Repetir con carrito lleno, cerrar sesión desde `/cuenta` y reingresar: deben persistir las cantidades; una cuenta distinta no debe ver los artículos de la anterior.
6. Reducir stock por un mecanismo controlado del backend, reabrir el carrito y verificar estado `no disponible` y bloqueo de checkout. Este paso no se realiza automáticamente en dev.

## CI
- `node --experimental-strip-types --test apps/web/src/lib/cart.test.mjs`
- `npm run lint:web`, `npm run typecheck:web`, `npm run build:web`
- `scripts/ci/e2e-cart.mjs`: Chromium → Next.js → API Spring Boot → PostgreSQL aislado. Verifica registro/login, sumar artículo desde UI, editar cantidad, persistencia tras logout/login y eliminar artículos.
