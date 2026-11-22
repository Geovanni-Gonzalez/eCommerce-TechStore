# eCommerce TechStore

Prototipo académico de tienda B2C de accesorios tecnológicos para el Proyecto #3 de Diseño de Software. Se trabajará con arquitectura monolítica modular, frontend y backend separados.

**Entrega académica actualizada:** sábado 21 de noviembre de 2026, 10:00 p. m. (Costa Rica), según reprogramación comunicada por el equipo.

## Manual operativo actualizado (BKL-063)

Para diagnóstico de frontend/backend, pruebas, migraciones, decisiones de
seguridad y recuperación, consultar
[Manual de operación BKL-063](docs/ops/BKL-063_OPERATIONS_MANUAL.md).
Los scripts de respaldo de prueba y salud preparados en BKL-061 están en
[su runbook](docs/ops/BKL-061_BACKUP_AND_HEALTH.md). Ni el respaldo remoto
ni un entorno staging activo se consideran terminados sin evidencia.

## Estructura

```text
eCommerce-TechStore/
├── apps/
│   ├── web/                       # Next.js, TypeScript, App Router
│   └── api/                       # Spring Boot, Java 21, Maven
├── infrastructure/
│   └── compose.yaml               # PostgreSQL para desarrollo
├── docs/
│   └── architecture/MONOREPO.md
├── package.json                   # npm workspaces (solo frontend)
└── README.md
```

## Requisitos previos

- **Node.js 22** recomendado (mínimo 20.9) y npm.
- **Java 21** (JDK) y **Maven 3.9+**.
- **Docker + Docker Compose** para ejecutar PostgreSQL (opcional en esta base, se utilizará en BKL-011).

## Iniciar el frontend

Desde la raíz:

```bash
npm install
npm run dev:web
```

Abrir **http://localhost:3000**. Deberías ver la página de inicio de TechStore.

Para verificar tipos o generar un build:

```bash
npm run typecheck:web
npm run build:web
```

> El archivo `package-lock.json` está versionado. Utiliza `npm ci` cuando quieras reproducir la instalación registrada.

## Iniciar el backend

En otra terminal, desde la raíz:

```bash
npm run dev:api
```

Alternativamente:

```bash
mvn -f apps/api/pom.xml spring-boot:run
```

Visita **http://localhost:8080/api/health**; debe responder:

```json
{"status":"UP","service":"techstore-api"}
```

Ejecutar prueba del backend:

```bash
npm run test:api
```

> El endpoint de salud responde independientemente de la base, pero desde BKL-011 el arranque completo de la API necesita que PostgreSQL esté disponible y configurado.

## Preparar PostgreSQL local

```bash
docker compose -f infrastructure/compose.yaml up -d
docker compose -f infrastructure/compose.yaml ps
```

Desactivar los contenedores (sin eliminar datos):

```bash
docker compose -f infrastructure/compose.yaml down
```

Los valores predeterminados son **solo para desarrollo local**, están ligados a localhost y deben reemplazarse antes de compartir cualquier instancia.

## Variables de entorno y seguridad

- `apps/web/.env.example` documenta `NEXT_PUBLIC_API_URL` para conectarse al backend.
- `infrastructure/.env.example` describe credenciales de ejemplo de PostgreSQL.
- Copiar los archivos de ejemplo a `.env.local` y `infrastructure/.env` únicamente cuando haga falta; estos archivos reales están ignorados por Git.
- Nunca subir contraseñas, tarjetas, claves de API, tokens ni datos personales al repositorio.


## BKL-011 — conexión a PostgreSQL y migraciones Flyway

1. Asegura que Docker Desktop esté ejecutándose. Desde la raíz, crea la configuración local (solo una vez):

   ```powershell
   Copy-Item infrastructure/.env.example infrastructure/.env
   ```

   **Solo para desarrollo local:** revisa `infrastructure/.env` antes de usarla. La contraseña de ejemplo no debe usarse para datos reales o desplegar públicamente.

2. Levanta PostgreSQL y comprueba su estado:

   ```powershell
   docker compose -f infrastructure/compose.yaml up -d
   docker compose -f infrastructure/compose.yaml ps
   ```

3. En la misma terminal en la que ejecutarás Maven, configura la contraseña de desarrollo local:

   ```powershell
   $env:SPRING_DATASOURCE_PASSWORD = "techstore_dev_only"
   npm run dev:api
   ```

   Si configuraste otra contraseña en la base, utiliza la misma. **No guardes contraseñas reales en Git ni publiques capturas donde aparezcan.**

4. Al iniciar, **Flyway** ejecuta automáticamente `V1__create_catalog_tables.sql` una vez y registra el resultado en `flyway_schema_history`. Compruébalo desde otra terminal:

   ```powershell
   docker exec techstore-postgres psql -U techstore -d techstore -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
   docker exec techstore-postgres psql -U techstore -d techstore -c "\dt"
   ```

   Se esperan las tablas `categories`, `products` y `flyway_schema_history` y una migración V1 exitosa. Al reiniciar la API la migración **no** debe ejecutarse de nuevo.

5. Ejecuta la prueba unitaria del backend:

   ```powershell
   npm run test:api
   ```

   > Esta prueba unitaria no requiere PostgreSQL. La prueba real de Flyway se confirma al **arrancar la API con la base funcionando** y consultar el historial de migraciones.

6. Si la API no consigue acceder a PostgreSQL: comprueba `docker compose ps`, el puerto 5432 y que la contraseña proporcionada en `SPRING_DATASOURCE_PASSWORD` coincida con la contraseña **con la que se inicializó el volumen**. Cambiar `POSTGRES_PASSWORD` después de crear el volumen **no** modifica automáticamente la contraseña de un usuario ya existente.

**Precaución:** `docker compose down` detiene los contenedores y conserva los datos; `docker compose down -v` elimina el volumen de datos y no debe usarse sin querer borrar la base.

**Alcance BKL-011:** se crean las tablas iniciales de catálogo con Flyway y se configura JDBC. Los controladores, entidades Java, pedidos y autenticación llegarán en sus respectivos issues; esta migración no implementa aún las funcionalidades del negocio.


## BKL-010 — base modular del backend y Actuator

La estructura Java incluye `config`, `shared`, `catalog`, `users`, `cart` y `orders`. Los módulos de dominio contienen por ahora archivos `package-info.java` para documentar sus responsabilidades; sus clases de negocio se crearán en los issues correspondientes.

### Comprobaciones

1. Inicia Docker Desktop y PostgreSQL como se explica arriba. En PowerShell, utiliza **Java 21** y configura localmente `SPRING_DATASOURCE_PASSWORD` antes de ejecutar `npm run dev:api`.
2. Comprueba la salud de la aplicación:

   ```powershell
   Invoke-RestMethod http://localhost:8080/actuator/health
   Invoke-RestMethod http://localhost:8080/api/health
   ```

   Actuator debe devolver al menos `status: UP`; `/api/health` mantiene su respuesta histórica con `service: techstore-api`. El endpoint de Actuator refleja también la salud de los componentes autoconfigurados, incluyendo PostgreSQL cuando corresponda.

3. Comprueba CORS desde otra terminal (simula la solicitud del navegador Next.js):

   ```powershell
   $headers = @{ Origin = "http://localhost:3000" }
   $response = Invoke-WebRequest http://localhost:8080/api/health -Headers $headers
   $response.Headers["Access-Control-Allow-Origin"]
   ```

   El resultado debe ser `http://localhost:3000`. Esto prueba la configuración de CORS para rutas `/api/**`; no implica que el frontend ya consuma la API.
4. Ejecuta pruebas:

   ```powershell
   npm run test:api
   ```

El valor predeterminado de `CORS_ALLOWED_ORIGINS` permite únicamente `http://localhost:3000` y `http://127.0.0.1:3000`; puede sobrescribirse mediante la variable de entorno homónima, separando varios orígenes con comas. No se habilitan cookies cross-origin y no se expone toda la información de Actuator.

## BKL-009 — estructura y herramientas del frontend

- Next.js App Router, TypeScript, layout reutilizable con encabezado y navegación accesible.
- Rutas base `/`, `/productos`, `/carrito` y `/login`. **Son páginas de estructura**; todavía no implementan catálogo, carrito ni autenticación.
- Cliente HTTP tipado `apps/web/src/lib/api.ts`: exporta `apiGet<T>()` y `ApiError`, usa `NEXT_PUBLIC_API_URL` (por defecto `http://localhost:8080`).
- Configuración oficial de ESLint para Next.js 16 con reglas Core Web Vitals y TypeScript.

**Preparación local importante:** BKL-009 incorpora las dependencias `eslint` y `eslint-config-next`, pero **`package-lock.json` aún debe actualizarse**. En la raíz del repositorio, después de cambiar a esta rama:

```powershell
npm install
npm run lint:web
npm run typecheck:web
npm run build:web
npm run dev:web
```

`npm install` actualiza `package-lock.json`. **Versionar y subir ese cambio en la misma rama del PR antes de fusionarlo**; de lo contrario, `npm ci` puede fallar por manifiesto y lockfile desincronizados. En CI, utilizar `npm ci` solo después de actualizar el lockfile.

La cabecera y el menú comparten semántica `header/nav/main/footer`, enlace para saltar al contenido y foco visible. Las páginas base proporcionan mensajes de alcance para no aparentar funcionalidades aún inexistentes.

Si el backend está disponible, el frontend podrá consumir `http://localhost:8080/api/**`, que BKL-010 habilitó con CORS para el origen `http://localhost:3000`.

## BKL-013 — Contrato REST y OpenAPI inicial

El contrato de referencia se encuentra en [`docs/api/openapi.json`](docs/api/openapi.json), y las convenciones en [`docs/api/REST_CONVENTIONS.md`](docs/api/REST_CONVENTIONS.md). Documenta 8 operaciones para salud, catálogo, registro/ingreso y pedidos.

**Importante:** solamente `GET /api/health` está implementado. Las otras 7 operaciones son contratos **planificados** para próximos issues; no son endpoints funcionales ni se han ejecutado contra Spring Boot.

Validación reproducible desde la raíz, con Node.js:

```powershell
npm run validate:api-contract
npm run test:api-contract
```

El primer script comprueba JSON, referencias, operación/respuestas, Problem Details y seguridad de pedidos; el segundo ejecuta pruebas positivas y negativas del validador. Ambos scripts usan únicamente módulos estándar de Node (sin nuevas dependencias npm). La prueba de conformidad completa de OpenAPI y las pruebas HTTP se harán conforme se implementen los controladores.

## Estado del proyecto

Los issues **BKL-008 y BKL-011** preparan el monorepositorio, PostgreSQL, JDBC y las migraciones. **BKL-010** añade módulos de backend, Actuator y CORS local.

**No implementa todavía:** registro de usuarios, catálogo conectado a la API, carrito, pedidos ni pagos; tampoco los patrones GoF. Cada funcionalidad se construirá en su issue, sin prometer una tienda operativa al completar el esqueleto.

La entrega del curso requiere MVC, al menos tres patrones GoF (Strategy, Facade y Observer propuestos), exposición con UX/UI/accesibilidad y **repositorio público**. Antes de cambiar la visibilidad debe revisarse que no existan secretos.

Consulta [documentación de arquitectura](docs/architecture/MONOREPO.md), [plan de entrega](docs/PLAN_ENTREGA_19_NOV_2026.md) y [GitHub Issues](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues).

## BKL-012 — Integración continua (GitHub Actions)

El workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) comprueba automáticamente los Pull Requests dirigidos a `dev` o `main`, los pushes a esas ramas y permite ejecución manual desde Actions. Los jobs se llaman **`Frontend - lint, tipos, contrato, build y smoke`** y **`Backend - compilar y probar Maven`**.

- **Frontend (Node.js 22):** `npm ci`, ESLint, `tsc --noEmit`, validación y pruebas del contrato OpenAPI, build de Next.js y prueba de humo HTTP de `/`, `/productos`, `/carrito` y `/login` utilizando el servidor de producción.
- **Backend (Java 21):** `mvn -B -ntp -f apps/api/pom.xml clean verify`. Las pruebas actuales son unitarias, por lo que no necesitan PostgreSQL; integrar pruebas reales contra PostgreSQL se hará cuando se incorporen repositorios/servicios de negocio.
- Se utilizan permisos mínimos de GitHub (`contents: read`), cachés npm/Maven, límites de tiempo y cancelación de ejecuciones obsoletas.
- **Importante:** agregar un workflow **no bloquea merges automáticamente**. Para cumplir completamente BKL-012, configurar en GitHub `Settings → Rules → Rulesets` (o `Settings → Branches → Branch protection rules`) una regla para `dev` que exija los dos checks del workflow, y verificarla con un PR. En un repositorio privado, la disponibilidad de reglas de protección puede depender del plan/permisos de GitHub.

### Reproducción local

```powershell
npm ci
npm run lint:web
npm run typecheck:web
npm run validate:api-contract
npm run test:api-contract
npm run build:web
mvn -B -ntp -f apps/api/pom.xml clean verify
```

El resultado de GitHub Actions debe verificarse en la pestaña **Checks** del PR antes del merge. Si un job falla, revisar su log y corregir la rama; no forzar el merge.


## BKL-018 — API pública de productos (rama de desarrollo)

El controlador `ProductController` expone dos consultas públicas:

| Solicitud | Respuesta |
|---|---|
| `GET /api/products?page=0&size=12` | `200`: `ProductPage` con productos activos; página desde 0 y tamaño de 1 a 100 |
| `GET /api/products/{id}` | `200`: producto activo; `404`: inexistente o inactivo |
| Parámetros inválidos (por ejemplo, `page=-1`, `size=101`, `id=0`) | `400`: `application/problem+json` |

El controlador solo traduce HTTP; las reglas y metadatos de paginación son responsabilidad de `ProductService`. El servicio utiliza la abstracción `ProductRepository` y la implementación `JdbcProductRepository` realiza consultas parametrizadas contra PostgreSQL. Los DTOs separan el modelo interno del JSON. Los campos opcionales `category` e `imageUrl` se omiten cuando no existen datos completos para responderlos.

**Prueba automatizada:** `mvn -f apps/api/pom.xml clean verify` ejecuta pruebas unitarias del servicio y pruebas HTTP con MockMvc. Estas últimas utilizan un repositorio falso: **no son pruebas de integración con PostgreSQL**.

**Prueba manual con PostgreSQL local:**

```powershell
docker compose -f infrastructure/compose.yaml up -d
$env:SPRING_DATASOURCE_PASSWORD = "techstore_dev_only"
npm run dev:api
```

En otra consola:

```powershell
Invoke-RestMethod "http://localhost:8080/api/products?page=0&size=12"
Invoke-WebRequest "http://localhost:8080/api/products/999"
```

Con una base vacía, la primera solicitud debe devolver `items: []`, `totalElements: 0` y `totalPages: 0`; la segunda debe responder HTTP 404. Para probar listados reales se necesitan productos activos insertados de forma reproducible. La interfaz Next.js todavía no está conectada a este endpoint.


## BKL-018 — Integración real con PostgreSQL y demo reproducible

La rama incorpora pruebas de dos niveles: **unitarias/HTTP aisladas** (`mvn test`) y **PostgreSQL + Flyway + JDBC + HTTP** (`mvn verify`, mediante Failsafe y `ProductCatalogIT`). El workflow de CI levanta PostgreSQL 17 efímero (`techstore_test`) y ejecuta ambos niveles, además de un smoke HTTP sobre la API ejecutándose como proceso Java.

### Ejecutar pruebas de integración en Windows

Usa Docker Desktop y el PostgreSQL local ya configurado. Crea una base de datos **separada** para las pruebas (solo si no existe):

```powershell
docker compose -f infrastructure/compose.yaml up -d
docker exec techstore-postgres psql -U techstore -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='techstore_test'"
# Si el resultado está vacío:
docker exec techstore-postgres createdb -U techstore techstore_test
```

Configura la contraseña **solo en tu terminal**, sin guardarla en Git (la siguiente es la clave local de ejemplo; utiliza tu clave si la cambiaste):

```powershell
$env:DB_TEST_PASSWORD = "techstore_dev_only"
mvn -f apps/api/pom.xml clean verify
```

`ProductCatalogIT` usa `application-integration.yml` y se niega a modificar cualquier base cuyo nombre no sea **`techstore_test`**. Sus datos se escriben en transacciones que se revierten. **No ejecutes estas pruebas contra la base de desarrollo `techstore` ni contra producción**. Para ejecutar únicamente las pruebas unitarias y MockMvc aislado, usa `mvn -f apps/api/pom.xml clean test` (no requiere PostgreSQL).

### Insertar catálogo de demostración local (opcional)

Esta operación modifica solo la base `techstore` de desarrollo; es manual e idempotente y **no forma parte de Flyway**:

```powershell
docker cp scripts/dev/seed-catalog.sql techstore-postgres:/tmp/seed-catalog.sql
docker exec techstore-postgres psql -U techstore -d techstore -v ON_ERROR_STOP=1 -f /tmp/seed-catalog.sql
```

Agrega dos productos activos (`DEMO-MOUSE-01`, `DEMO-KEY-01`) y uno inactivo (`DEMO-HIDDEN-01`). En una terminal, inicia la API con `SPRING_DATASOURCE_PASSWORD` configurada y luego consulta:

```powershell
Invoke-RestMethod "http://localhost:8080/api/products?page=0&size=12"
```

Los activos deben aparecer, el inactivo no; el detalle de un producto inexistente responde 404. El script `scripts/ci/smoke-catalog.py` prueba ese comportamiento sobre HTTP en la base efímera de GitHub Actions. **No modifiques `V1__create_catalog_tables.sql`** para agregar datos de prueba.

## BKL-014 — Persistencia de cuentas y roles

La migración V2 añade `users` con correo único normalizado, hash BCrypt, roles `CLIENTE`/`ADMIN` y fechas de auditoría. Se implementan los contratos y las clases de persistencia y hashing para los futuros casos de uso de registro/autenticación. **Los endpoints auth siguen pendientes** (BKL-015/BKL-016).

Ver [documentación del módulo de usuarios](docs/backend/USER_ACCOUNTS.md) para diseño SOLID/GRASP, pruebas y configuración de la base `techstore_test`.

## BKL-015 — Registro seguro de clientes

`POST /api/auth/register` crea una cuenta `CLIENTE` y devuelve `201` con `{id, displayName, email, role: "CLIENT"}`. Los errores de validación usan `400 application/problem+json` y los correos duplicados `409`. La contraseña se almacena únicamente como hash BCrypt (máximo 72 bytes UTF-8 para evitar truncamiento); jamás aparece en la respuesta. La migración Flyway V3 incorpora `display_name`.

**Pruebas:** `mvn -f apps/api/pom.xml clean test` (unitarias/MockMvc) o, con la base aislada `techstore_test`, `mvn -f apps/api/pom.xml clean verify` (incluye PostgreSQL real). GitHub Actions también ejecuta `scripts/ci/smoke-register.py` por HTTP contra el JAR. El registro **no** inicia sesión; login/logout quedan para BKL-016.

Ver [diseño y documentación del registro](docs/backend/REGISTER.md).

## BKL-016 — Login, logout y autorización Bearer

`POST /api/auth/login` autentica correo/contraseña BCrypt y devuelve `{accessToken, tokenType:"Bearer", user}`. El token es opaco, aleatorio (256 bits), caduca en ocho horas y solo su SHA-256 se almacena en PostgreSQL por medio de **Flyway V4**. `POST /api/auth/logout` revoca ese token inmediatamente (204); `GET /api/auth/me` exige autenticación y `GET /api/admin/check` exige rol ADMIN (401/403 Problem Details). Spring Security mantiene públicos los endpoints de salud, catálogo y registro.

No se crea una interfaz administrativa ni se implementan pedidos todavía. Los tokens Bearer **no son JWT** y no se deben imprimir, guardar en Git o persistir en `localStorage`. Ver [decisiones, pruebas y amenazas pendientes de autenticación](docs/backend/AUTHENTICATION.md). Ejecutar `mvn -f apps/api/pom.xml clean test` o, con PostgreSQL `techstore_test` configurado, `mvn -f apps/api/pom.xml clean verify`.

## BKL-028 — Carrito persistente de clientes

El backend expone `GET /api/cart`, `POST /api/cart/items`, `PATCH /api/cart/items/{productId}`, `DELETE /api/cart/items/{productId}` y `DELETE /api/cart`, accesibles con Bearer **CLIENT**. Flyway V5 incorpora `carts` y `cart_items` por usuario. El carrito no acepta precios del navegador: subtotal CRC y disponibilidad se calculan desde PostgreSQL. **No reserva stock** ni implementa checkout/pedidos; el frontend editable queda pendiente (BKL-029).

Ejecutar `mvn -f apps/api/pom.xml clean test`; para pruebas PostgreSQL reales sobre la base aislada `techstore_test`, ejecutar `mvn -f apps/api/pom.xml clean verify`. CI también ejecuta `scripts/ci/smoke-cart.py` por HTTP. Consultar [documentación del carrito](docs/backend/CART.md).

## BKL-030 — Modelo de Pedido y DetallePedido

Flyway **V6** introduce `orders` y `order_items`, con estado inicial **PENDING**, pago exclusivamente **SIMULATED**, dirección de entrega copiada y snapshots históricos de cada producto (nombre, precio unitario, cantidad y total de línea). Las entidades Java `OrderDraft`, `Order`, `ShippingAddress`, `OrderItemSnapshot` y `OrderTotals` validan montos en CRC con `BigDecimal`. El puerto `OrderRepository` y su adaptador `JdbcOrderRepository` escriben cabecera e ítems en una transacción y limitan las consultas al propietario.

**No se habilitaron todavía endpoints de pedidos ni se reserva stock:** los tres endpoints `/api/orders` siguen marcados como `planned` en OpenAPI hasta implementar validación de precio/stock y checkout (BKL-032/BKL-033). BKL-007 (revisión ERD integral) sigue pendiente. Ejecutar `mvn -f apps/api/pom.xml clean test` y, con `techstore_test` configurada, `mvn -f apps/api/pom.xml clean verify`. Leer [decisiones y pruebas del modelo de pedidos](docs/backend/ORDER_MODEL.md).

## BKL-032 — Cálculo de checkout con Strategy

`GET /api/checkout/quote` es un endpoint protegido (Bearer con rol CLIENT) que lee el carrito del usuario y calcula `subtotalCrc`, `shippingCrc`, `taxCrc` y `totalCrc` desde precios **actuales** de PostgreSQL, sin aceptar importes del navegador. Aplica el patrón **GoF Strategy** mediante dos políticas intercambiables: tarifa fija `FIXED` y envío gratuito sobre umbral `FREE_OVER_THRESHOLD`.

La tasa ilustrativa (predeterminado 13 %), la tarifa (₡2500), el umbral (₡30000) y la estrategia se configuran con variables de entorno `CHECKOUT_TAX_RATE`, `CHECKOUT_SHIPPING_FEE_CRC`, `CHECKOUT_FREE_SHIPPING_THRESHOLD_CRC` y `CHECKOUT_SHIPPING_POLICY`. La respuesta devuelve `estimateOnly: true`: **no** reserva stock, crea pedidos ni procesa pagos. `409` indica productos indisponibles y `422` carrito vacío. Ver [BKL-032: fórmulas, clases del patrón y pruebas](docs/backend/CHECKOUT_PRICING.md).

## BKL-033 — Pedido transaccional con Facade

`POST /api/orders` confirma un carrito del usuario autenticado **CLIENT** con únicamente `shippingAddress` en el JSON. La **CheckoutFacade** (GoF Facade) bloquea carrito y productos, recalcula precios e impuestos/envío con Strategy (BKL-032), crea el pedido PENDING/SIMULATED con snapshots históricos (BKL-030), descuenta el stock y registra reservas con Flyway **V7**, y vacía el carrito, **todo en una transacción PostgreSQL**. Errores controlados: 400, 401, 403, 409 o 422. Se añadieron pruebas de concurrencia real por última unidad y rollback tras un fallo posterior a la reserva.

**Sin pagos reales ni confirmación definitiva:** la reserva no caduca automáticamente; su liberación/consumo orquestados quedan para BKL-027/BKL-045. `OrderCreatedEvent` se integra con el **Observer GoF de BKL-068**: auditoría posterior al commit mediante listener desacoplado y Flyway V8 (entrega best effort, sin outbox). Documentación: [BKL-033 checkout y diseño Facade](docs/backend/CHECKOUT_ORDER.md). Ejecutar `mvn -f apps/api/pom.xml clean test` o, con `techstore_test`, `mvn -f apps/api/pom.xml clean verify`.


## BKL-068 — Observer GoF para pedidos

La creación de pedidos notifica `OrderCreatedEvent` sin acoplar `CheckoutFacade` a la reacción. `OrderAuditListener` observa **AFTER_COMMIT** y `JdbcOrderEventAuditRepository` persiste una fila idempotente en `order_event_audit` (Flyway V8) con **REQUIRES_NEW**. No hay auditoría si el checkout hace rollback; no se envían correos, y no hay garantía de entrega tras un crash (no outbox). Ver [diagrama UML, demo y pruebas](docs/backend/ORDER_OBSERVER.md).


## BKL-027 — Liberación y vencimiento transaccionales de reservas

La API admite `POST /api/orders/{id}/cancel` con Bearer **CLIENT** propietario: un pedido `PENDING` se cancela y devuelve inventario exactamente una vez (repetir da 204); ajenos 404 y `CONFIRMED` 409. Flyway **V9** registra caducidad por reserva a **30 minutos**, y un recolector cada **60 segundos** cancela automáticamente pedidos vencidos. Es configurable mediante `CHECKOUT_RESERVATION_EXPIRY_ENABLED` y `CHECKOUT_RESERVATION_EXPIRY_SCAN_MS`; en pruebas de integración está deshabilitado y se invoca manualmente. **No procesa pagos**, no confirma pedidos y no cubre la máquina de estados administrativa BKL-045. Guía y pruebas: [Ciclo de reservas BKL-027](docs/backend/RESERVATION_LIFECYCLE.md).


## BKL-045 — Estados de pedidos con historial auditable

Flyway **V10** añade `order_status_history`. El checkout registra `CREATED` y las cancelaciones del cliente, el scheduler y ADMIN registran `CLIENT_CANCEL`, `EXPIRED` o `ADMIN_CANCEL`, siempre en la misma transacción que devuelve stock. `PATCH /api/admin/orders/{id}/status` permite cancelar PENDING (Bearer ADMIN) y `GET /api/admin/orders/{id}/history` consulta la traza. **CONFIRMED queda exclusivamente para el flujo de pagos BKL-040**. Ver [BKL-045 — historial y transiciones](docs/backend/ORDER_STATUS_LIFECYCLE.md).


## BKL-035 — Puerto de pagos y sandbox seguro

`PaymentGateway` define el contrato desacoplado para una autorización simulada. En configuración predeterminada, **DISABLED** rechaza solicitudes y **no crea cobros**. Para una demostración controlada y **sin proveedor externo**, habilitar expresamente `TECHSTORE_PAYMENT_PROVIDER=SANDBOX` y `TECHSTORE_PAYMENT_SANDBOX_ENABLED=true`; `TECHSTORE_PAYMENT_SANDBOX_OUTCOME=APPROVED|DECLINED` controla solo el resultado simulado. El sandbox no recibe datos de tarjetas ni credenciales, no llama a redes y **no confirma pedidos ni libera/consume inventario**. Los intentos persistentes y la idempotencia de pagos corresponden a BKL-036; la confirmación de pedidos ante pago real, a BKL-040. [Contrato, flujo y límites](docs/backend/PAYMENT_GATEWAY_SANDBOX.md).


## BKL-036 — Intentos de pago persistentes e idempotentes

La API incorpora `POST /api/orders/{orderId}/payment-attempts` (Bearer CLIENT, `Idempotency-Key`, **sin body**) y `GET /api/orders/{orderId}/payment-attempts/{attemptId}`. Flyway **V11** guarda intentos y referencias simuladas únicas, y evita reenviar autorizaciones al repetir claves; estados inciertos quedan pendientes de conciliación. El proveedor sigue deshabilitado por defecto y **SIMULATED_APPROVED no confirma pedidos ni acredita cobros reales**. Véase [BKL-036 — intentos, concurrencia y conciliación](docs/backend/PAYMENT_ATTEMPTS.md).


## BKL-038 — Webhook de sandbox autenticado y deduplicado

**Flyway V12** añade el ledger `payment_webhook_events` con claves únicas por evento e intento; `POST /api/payments/webhooks/sandbox` recibe HMAC-SHA256 firmado sobre el body exacto y timestamp ±5 minutos. Está **deshabilitado por defecto** y solo se habilita con `TECHSTORE_PAYMENT_WEBHOOK_ENABLED=true` y un secreto aleatorio fuerte `TECHSTORE_PAYMENT_WEBHOOK_SECRET` suministrado fuera del repositorio. Se registra un evento válido una única vez sin confirmar pedidos ni afectar inventario. Este webhook sigue siendo una simulación sin pasarela bancaria real; BKL-037 añade una redirección local segura. Véase [seguridad, contrato y conciliación BKL-038](docs/backend/WEBHOOK_VERIFICATION.md).


## BKL-037 — Inicio seguro y redirección al sandbox

**Flyway V13** agrega sesiones de checkout con tokens aleatorios de 256 bits (solo hash en PostgreSQL), vigencia máxima de 10 minutos y canje de un solo uso. Un **CLIENT autenticado** inicia `POST /api/orders/{orderId}/payment-sessions` con `Idempotency-Key` y sin body: el servidor valida propiedad, estado de reserva, monto histórico y moneda CRC, y produce una URL fija de `/pagos/sandbox#session=...`. La página local previsualiza el monto y simula una autorización; no procesa dinero ni confirma pedidos. Todo está **deshabilitado por defecto** y requiere `TECHSTORE_PAYMENT_PROVIDER=SANDBOX` + `TECHSTORE_PAYMENT_SANDBOX_ENABLED=true`. [Guía completa BKL-037](docs/backend/SECURE_PAYMENT_REDIRECT.md).


## BKL-040 — Confirmación y consumo atómico (solo sandbox opt-in)

Flyway **V14** registra el estado de conciliación de webhooks y el motivo de transición `SANDBOX_VERIFIED`. Con `TECHSTORE_PAYMENT_SANDBOX_AUTO_CONFIRM=true` **solo en pruebas**, un evento SANDBOX firmado, aprobado y coincidente con el intento/pedido permite pasar atómicamente el pedido a `CONFIRMED`, reservas a `CONSUMED` e historial, sin restar stock de nuevo. Por defecto no confirma pedidos. Los eventos declinados/tardíos quedan `DECLINED` o `NEEDS_REVIEW`, y fallos de transacción conservan `RECEIVED` para retry ADMIN. **No son cobros bancarios.** [Diseño y controles BKL-040](docs/backend/ATOMIC_PAYMENT_CONFIRMATION.md).


## BKL-041 — Rechazo, caducidad y conciliación incierta

Flyway **V15** registra incidencias de autorizaciones inciertas, aprobaciones sin conciliar y webhooks tardíos. Cancelación y vencimiento conservan stock reservado ante `STARTED/UNKNOWN/SIMULATED_APPROVED` potencialmente ejecutados; la única excepción es una sesión hosted `STARTED` **que nunca se canjeó**, ya que no realizó autorización. Rechazos y `UNAVAILABLE` pueden liberarse según caducidad. Endpoint `GET /api/admin/orders/{orderId}/payment-incidents` (solo ADMIN), sin posibilidad de forzar pago o devolución. [Política detallada BKL-041](docs/backend/PAYMENT_FAILURE_RECONCILIATION.md).


## BKL-042 — Consultas seguras de pedidos

Las rutas **CLIENT** `GET /api/orders`, `GET /api/orders/{id}` y `GET /api/orders/{id}/history` filtran por el usuario autenticado, usan paginación por cursor `limit`/`beforeId` y devuelven 404 para pedidos ajenos o inexistentes. Las rutas **ADMIN** `GET /api/admin/orders` y `GET /api/admin/orders/{id}` usan DTO separados que muestran el propietario. El historial CLIENT no divulga IDs de actores ni auditoría. OpenAPI **31 operaciones**. [Detalles de BKL-042](docs/backend/ORDER_READ_API.md).


## BKL-039 — Páginas de resultado de pago (solo sandbox)

La ruta `/pagos/resultado` y las vistas `/aprobado`, `/rechazado`, `/pendiente` **nunca aceptan el estado de una URL como evidencia**. Solicitan identificación de la cuenta propietaria y consultan `GET /api/orders/{id}` y `GET /api/orders/{id}/payment-attempts/{attemptId}`; solo muestran aprobación de **simulación** con pedido `CONFIRMED` e intento `SIMULATED_APPROVED` concordantes. El intento aprobado sin webhook conciliado aparece pendiente. Hay pruebas de clasificación negativa y smoke de rutas en CI. [Guía de BKL-039](docs/frontend/PAYMENT_RESULT_PAGES.md).


## BKL-043 — Mis pedidos: historial, detalle y estado visual

La navegación **Mis pedidos** abre `/pedidos`, una interfaz privada ROLE_CLIENT con listado paginado por cursor (12 por página), estado `PENDING/CONFIRMED/CANCELLED`, detalle comercial histórico y línea temporal de BKL-042. El cliente consulta solo pedidos propios; la sesión puntual se mantiene en memoria (BKL-017 pendiente). `CONFIRMED` se identifica claramente como sandbox sin dinero bancario. [Guía BKL-043](docs/frontend/ORDER_HISTORY_UI.md).


## BKL-044 — Pruebas E2E de checkout, pagos y pedidos

CI ejecuta un recorrido **Chromium → Next.js → Spring Boot → PostgreSQL** en dos escenarios sandbox separados (**APPROVED** y **DECLINED**). Comprueba estado pendiente sin conciliación, aprobación simulada tras webhook HMAC verificado, rechazo, replays idempotentes, aislamiento entre cuentas, stock y timeline de pedidos. El carrito, cotización y creación del pedido se preparan por HTTP real porque el checkout visual sigue pendiente de otros issues. [Plan y matriz E2E](docs/qa/BKL-044_E2E_CHECKOUT_PAYMENTS.md).
