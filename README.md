# eCommerce TechStore

Prototipo académico de tienda B2C de accesorios tecnológicos para el Proyecto #3 de Diseño de Software. Se trabajará con arquitectura monolítica modular, frontend y backend separados.

**Entrega académica actualizada:** sábado 21 de noviembre de 2026, 10:00 p. m. (Costa Rica), según reprogramación comunicada por el equipo.

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
