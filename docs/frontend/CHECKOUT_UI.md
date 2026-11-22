# BKL-034 — Checkout visual: entrega, revisión y pago sandbox

Issue: https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/35

## Recorrido

1. Cliente CLIENT inicia sesión (BKL-017), agrega productos desde el catálogo
   (BKL-019 y BKL-029) y entra al carrito.
2. Desde `/carrito`, pulsa **Preparar dirección de entrega** (BKL-031).
   Los seis campos se validan según `ShippingAddress.java`.
3. Tras guardar la dirección, pulsa **Continuar a revisión del pedido**;
   `/checkout/revision` consulta `GET /api/cart` y
   `GET /api/checkout/quote` mediante Bearer opaco del cliente.
4. El servidor determina precio, impuesto, envío y total. La UI muestra el
   desglose CRC, artículos y la dirección; permite regresar a editar sin
   perder el borrador en memoria. La cotización es **provisional**, no bloquea
   inventario ni garantiza precio; cambios de stock se manejan con error.
5. **Confirmar pedido y reservar existencias** ejecuta exactamente una vez
   `POST /api/orders` con solo `{shippingAddress}`. El backend aplica
   `CheckoutFacade`, `ShippingStrategy`, bloquea carrito y productos,
   recalcula importes y reserva stock en una transacción PostgreSQL; devuelve
   pedido PENDING, que no equivale a pago confirmado.
6. Si el total definitivo cambió, la pantalla lo advierte y lo presenta.
   Solo tras revisar ese importe, **Continuar a pago simulado** envía
   `POST /api/orders/{id}/payment-sessions`, con header
   `Idempotency-Key` generado en navegador. No envía body, monto, tarjeta,
   retorno, token de sesión ni identidad en query string.
7. La respuesta del servidor se valida: pedido, monto CRC, moneda y URL
   exacta `/pagos/sandbox#session=...` en el mismo origen TechStore. El
   navegador navega a esa URL sin agregar parámetros. La página sandbox
   retira el secreto del fragmento inmediatamente y ejecuta la simulación.
   El usuario consulta luego el estado mediante el frontend existente
   `/pagos/resultado`, que exige nueva autenticación del propietario y
   verifica pedido e intento contra el backend, nunca por URL.

## Estados de error y seguridad

- Si falla la cotización, conserva el borrador de dirección y permite
  actualizar el carrito, reintentar y editar entrega.
- Si la confirmación devuelve 400/401/403/409/422, muestra el error y
  conserva la dirección para corregir o comprobar el carrito.
- Si la creación tiene resultado ambiguo (red o 5xx), **no repite
  automáticamente POST /api/orders**, pues el carrito pudo consumirse.
  Indica revisar Mis pedidos para evitar dobles confirmaciones.
- Tras crear pedido, un fallo del sandbox no vuelve a ejecutar POST /api/orders:
  mantiene el pedido y repite el inicio de sesión de pago con la **misma
  Idempotency-Key**, que el backend utiliza de forma idempotente.
- El navegador no almacena Bearer, dirección ni URL con token en cookies,
  localStorage, sessionStorage, query strings ni logs.
- Solo un pedido PENDING con reservas vigentes puede iniciar sesión sandbox.
  El proveedor está DISABLED por defecto; configurar opt-in en la API.
- Ningún pago es real ni involucra tarjeta; APPROVED simulado no confirma
  pedidos hasta conciliación HMAC verificada (BKL-040).
- La sesión BKL-017 vive solo en memoria. Al refrescar se pierde;
  el resultado sandbox puede exigir login de nuevo como ya hace BKL-039.

## Configuración local (solo demostración)

En entorno aislado y después de arrancar PostgreSQL:
```powershell
$env:TECHSTORE_PAYMENT_PROVIDER = "SANDBOX"
$env:TECHSTORE_PAYMENT_SANDBOX_ENABLED = "true"
$env:TECHSTORE_PAYMENT_SANDBOX_OUTCOME = "DECLINED"
$env:TECHSTORE_PAYMENT_SANDBOX_REDIRECT_ORIGIN = "http://localhost:3000"
npm run dev:api
```
Con Next.js en otra terminal: `npm run dev:web`. Usar cuentas de prueba.
Una aprobación artificial requiere cambiar `TECHSTORE_PAYMENT_SANDBOX_OUTCOME`
a `APPROVED`; no constituye cobro real. La confirmación automatizada
exige además HMAC y configuración de BKL-040.

## Pruebas

- `node --experimental-strip-types --test apps/web/src/lib/checkout-ui.test.mjs`
- `npm run lint:web`, `npm run typecheck:web`, `npm run build:web`
- CI: `scripts/ci/e2e-checkout-visual.mjs` con Next.js, Spring Boot,
  PostgreSQL y Chromium reales en matriz APPROVED y DECLINED.
- Regresiones BKL-019, BKL-017, BKL-029, BKL-031 y BKL-044 en CI.
