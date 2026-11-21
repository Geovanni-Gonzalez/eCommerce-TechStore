# BKL-019 — Catálogo y detalle Next.js con API pública

## Alcance

- `/productos` consulta **GET /api/products?page=N&size=12** vía `apiGet` en el navegador, sin datos falsos ni mocks. La página es pública.
- Cada tarjeta vincula `/productos/{id}`, donde se consulta **GET /api/products/{id}**. Los productos inexistentes o inactivos devuelven 404 y una vista sin detalles privados.
- Precios `priceCrc` y existencias `stock` salen exclusivamente del backend, formato CRC mediante `Intl.NumberFormat("es-CR")`.
- Lista adaptable, paginación con URL, enlaces por teclado, estados de carga/error/vacío, stock agotado y reintento accesible.
- Validación de DTO antes de renderizar: producto activo, ID positivo, dinero finito, stock entero no negativo, paginación consistente.
- No se envían tokens ni se necesita autenticación para GET catálogo.

## Limitaciones reales

**No se inventan fotografías de productos.** La respuesta de catálogo de BKL-018 incluye `imageUrl` opcional, pero la implementación V1 actualmente devuelve `null` (no hay imagen en la tabla). Si el backend entrega una URL HTTPS válida (o HTTP local), el componente la presenta con texto alternativo. Si falta o falla la carga, aparece el marcador accesible "Imagen no disponible". Para cumplir una foto real por producto, hace falta proporcionar URLs o archivos autorizados y adaptar catálogo/BD.

La ruta `/carrito` sigue provisional (BKL-029). Por ello no se afirma que "Añadir al carrito" funcione. El detalle ofrece información real y navegación de regreso; la compra visual es parte de BKL-029/031/034.

## Prueba manual (PowerShell)

Desde la raíz del repositorio con Node 22, JDK 21, Maven y Docker:

```powershell
Copy-Item infrastructure/.env.example infrastructure/.env   # solo si no existe
docker compose -f infrastructure/compose.yaml up -d
$env:SPRING_DATASOURCE_PASSWORD = "techstore_dev_only"
npm run dev:api
```

En otra terminal:

```powershell
docker cp scripts/dev/seed-catalog.sql techstore-postgres:/tmp/seed-catalog.sql
docker exec techstore-postgres psql -U techstore -d techstore -v ON_ERROR_STOP=1 -f /tmp/seed-catalog.sql
npm ci
npm run dev:web
```

Abrir `http://localhost:3000/productos`, confirmar mouse/teclado con precios y existencias de API; pulsar cada detalle; probar `/productos/999999` (no disponible), `/productos?page=abc` (parámetro inválido) y detener la API para comprobar reintento. El producto inactivo del seed no debe aparecer.

## Verificación automática

```powershell
node --experimental-strip-types --test apps/web/src/lib/catalog.test.mjs
npm run lint:web
npm run typecheck:web
npm run build:web
```

Las pruebas del contrato no requieren BD; la integración real HTTP depende de Spring y PostgreSQL. El checkout E2E de BKL-044 continúa independiente y no sustituye el recorrido visual completo del carrito.
