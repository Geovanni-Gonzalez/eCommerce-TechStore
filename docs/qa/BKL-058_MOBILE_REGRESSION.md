# BKL-058 — Regresión de checkout y pagos en escritorio/móvil

## Automatización

En cada matriz de CI (sandbox **APPROVED** y **DECLINED**) se ejecutan:
- `e2e-catalog.mjs`: catálogo y detalle en navegador.
- `e2e-auth.mjs`: registro, ingreso, cierre de sesión y roles.
- `e2e-cart.mjs`: carrito conectado al backend.
- `e2e-delivery.mjs`: dirección de Costa Rica.
- `e2e-checkout-visual.mjs`: registro → carrito → entrega →
  revisión → creación de pedido → sandbox → resultado seguro.
- `e2e-checkout-payments.mjs`: validación HMAC, intento,
  conciliación, IDOR, inventario y consulta de pedido en navegador.

**Nuevo BKL-058:** los dos escenarios de checkout se vuelven a ejecutar
con `E2E_VIEWPORT=mobile` (390×844), además de su viewport de
escritorio predeterminado (1280×850). Se utiliza PostgreSQL aislado y
Chrome sin conexión con pasarelas de pago reales.

## Criterios y observación

El resultado de CI debe ser verde para ambas columnas de matriz.
Los recorridos de catálogo y carrito fuera de checkout aún conservan
su viewport original; la revisión móvil adicional de búsqueda/filtros
y lectores de pantalla corresponde a BKL-054/BKL-069.

**Límite:** una única anchura móvil no sustituye dispositivos Android/iOS,
renderización WebKit, Firefox, evaluación de contraste ni un recorrido
visual manual. Se deben registrar defectos encontrados y revisión
cruzada antes de declarar el bloque Done.

## Ejecución

En el job E2E existente, después de iniciar API, web y fixtures:

```sh
node scripts/ci/e2e-checkout-visual.mjs
node scripts/ci/e2e-checkout-payments.mjs
E2E_VIEWPORT=mobile node scripts/ci/e2e-checkout-visual.mjs
E2E_VIEWPORT=mobile node scripts/ci/e2e-checkout-payments.mjs
```
