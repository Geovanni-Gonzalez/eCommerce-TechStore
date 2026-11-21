# BKL-044 — E2E real en Chromium: compra, pagos, idempotencia y PostgreSQL

## Objetivo y alcance verificable

La prueba automatizada recorre **Chromium → páginas reales Next.js → API Spring Boot → PostgreSQL** sin mocks del backend ni peticiones HTTP interceptadas. El prototipo tiene la pasarela **SANDBOX** deshabilitada por defecto y no efectúa cobros bancarios. El pipeline E2E habilita SANDBOX + webhook HMAC **únicamente contra PostgreSQL efímero `techstore_test`**.

**Limitación del producto actual:** `/carrito` y `/login` general siguen como páginas provisionales hasta BKL-019/BKL-031/BKL-017. Para evitar confundir una demo incompleta con un checkout visual funcional, la prueba prepara registro, carrito, cotización, checkout y arranque de sesión mediante **API HTTP real**. Después sí recorre **navegador real** por `/pagos/sandbox`, `/pagos/resultado` y `/pedidos` y valida las escrituras en PostgreSQL. Un E2E 100 % por UI desde el catálogo hasta checkout visual queda pendiente de esos issues. No sustituye la revisión independiente del código.

## Matriz de escenarios

El workflow CI incluye un job matricial independiente del frontend/backend unitario, con dos runners PostgreSQL **aislados**:

| Escenario | Trayecto comprobado | Evidencia SQL |
| --- | --- | --- |
| **APPROVED + PENDING** | Pantalla sandbox recibe SIMULATED_APPROVED; pantalla de resultado **continúa PENDIENTE** antes de notificación HMAC | `orders=PENDING`, reservas `RESERVED` |
| **APPROVED + webhook firmado** | Sin firma se rechaza; HMAC válido activa conciliación simulada; refrescar pantalla muestra **Simulación aprobada** | `orders=CONFIRMED`, reservas `CONSUMED`, un `SANDBOX_VERIFIED` |
| **DECLINED** | Sandbox devuelve SIMULATED_DECLINED y UI muestra **Simulación rechazada** | `orders=PENDING`, reservas `RESERVED`, sin `SANDBOX_VERIFIED` |
| **REPETIDO / idempotencia** | Mismo `Idempotency-Key` rota token de sesión y mantiene un intento; token anterior y doble canje devuelven 404; webhook duplicado devuelve 200 | Único intento, único evento firmado, sin doble descuento/consumo |
| **IDOR / anónimo** | Otro cliente no puede iniciar una sesión para pedido ajeno ni leer pedido/intento/historial. Visitar `/aprobado` y autenticarse como otra cuenta **no muestra éxito** | 404 ajeno indistinguible de 404 inexistente; cero divulgación |
| **Historial visual** | Cliente propietario abre `/pedidos`, ve pedido real, productos, dirección propia, motivos de auditoría y cierra sesión | `order_items`, `order_status_history`, snapshots y estado coherentes |

El test utiliza un correo aleatorio por ejecución, dos clientes distintos, productos de `scripts/dev/seed-catalog.sql` y el monto/moneda obtenido del servidor. Antes y después se comprueba el stock, la reserva y el número de intentos directamente en la BD; no se pueden alterar total/resultado desde el navegador. El token de pago se consume desde **fragmento URL** eliminado por la página, no se almacena en localStorage ni se imprime en logs.

## Ejecución CI

`.github/workflows/ci.yml` conserva jobs normales `web` y `api` y agrega **`e2e` con matrix `APPROVED` y `DECLINED`**. Cada job hace lo siguiente:

1. Inicializa PostgreSQL 17 `techstore_test` temporal, Java 21 y Node 22.
2. Compila el jar Spring Boot y la aplicación Next.js de producción.
3. Instala **Playwright 1.56.1 fijado** solo para el job E2E (no modifica `package-lock.json`) y el binario Chromium correspondiente.
4. Arranca API en `localhost:8080` con `TECHSTORE_PAYMENT_PROVIDER=SANDBOX`, `TECHSTORE_PAYMENT_SANDBOX_ENABLED=true`, `TECHSTORE_PAYMENT_SANDBOX_AUTO_CONFIRM=true` y webhook HMAC habilitado con **clave de prueba no productiva**. Arranca Next.js en `localhost:3000`.
5. Aplica migraciones Flyway durante startup, verifica salud, carga fixtures efímeras, y ejecuta `node scripts/ci/e2e-checkout-payments.mjs`.
6. Si falla la navegación, conserva una captura de la última pantalla como artefacto efímero de CI; cierra ambos procesos.

**No ejecutar el archivo contra producción ni una base de desarrollo normal.** El script valida `SELECT current_database() = 'techstore_test'` y un contenedor explícito. Los valores HMAC de CI son *fixtures* de demostración que nunca deben usarse como credenciales reales. El e-commerce no procesa tarjetas y `CONFIRMED` solo acredita una conciliación **simulada**.

## Pruebas complementarias y futuro

Se mantienen las **232 pruebas backend** (97 unitarias + 135 de integración PostgreSQL), las pruebas de clasificadores BKL-039, del historial BKL-043, OpenAPI, ESLint, TypeScript y smoke de navegación. Los E2E prueban flujos funcionales distintos de los tests unitarios y nunca alteran el estado del backend con mocks.

Trabajos futuros: implementar checkout visual BKL-019/BKL-031 y autenticación común BKL-017 para habilitar un *journey* completamente por navegador; añadir capturas de red y video solo en un entorno controlado sin PII si llegaran a necesitarse.
