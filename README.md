# eCommerce TechStore

TechStore es una aplicación web de comercio electrónico B2C para accesorios tecnológicos. Organiza el frontend y la API en un monorepositorio y utiliza PostgreSQL para persistencia.

## Tecnologías

| Componente | Tecnología | Ubicación |
| --- | --- | --- |
| Frontend | Next.js 16, React 19 y TypeScript | `apps/web/` |
| Backend | Spring Boot 4, Java 21 y Maven | `apps/api/` |
| Base de datos | PostgreSQL 17 y migraciones Flyway | `infrastructure/` y `apps/api/src/main/resources/db/migration/` |
| Contenedor backend | Docker multi-stage, Java 21 | `apps/api/Dockerfile` |
| Integración continua | GitHub Actions | `.github/workflows/ci.yml` |
| Despliegue web | Vercel | `apps/web/` |
| Plantilla de despliegue API y BD | Render Blueprint | `render.yaml` |

La API ofrece catálogo, autenticación, carrito, checkout, pedidos, seguimiento y reseñas. **Los pagos son de simulación (sandbox), no transacciones bancarias reales**. El proveedor de pagos y el de logística están desactivados por defecto.

## Estructura del repositorio

```text
apps/
  web/              Frontend Next.js
  api/              API Spring Boot y Dockerfile
infrastructure/
  compose.yaml      PostgreSQL local
  .env.example      Variables de PostgreSQL para desarrollo
docs/
  api/openapi.json  Contrato de la API
  deploy/           Instrucciones detalladas de despliegue
scripts/            Utilidades de desarrollo y operación
render.yaml         Blueprint opcional para Render
package.json        Comandos del monorepositorio
```

## Requisitos

- Node.js **22** y npm.
- JDK **21** y Maven **3.9+**.
- Docker con Docker Compose para PostgreSQL local.

## Configuración local

Todos los comandos se ejecutan desde la raíz del repositorio.

### 1. Preparar PostgreSQL

Copia el archivo de ejemplo y revisa sus valores. La configuración incluida es **solo para desarrollo local**.

```bash
cp infrastructure/.env.example infrastructure/.env
docker compose -f infrastructure/compose.yaml up -d
docker compose -f infrastructure/compose.yaml ps
```

El servicio utiliza PostgreSQL 17, el puerto `127.0.0.1:5432` y un volumen persistente. La base de datos y el usuario se definen mediante `POSTGRES_DB` y `POSTGRES_USER` en `infrastructure/.env`.

### 2. Configurar y ejecutar la API

En una terminal, establece `SPRING_DATASOURCE_PASSWORD` con **el mismo valor que `POSTGRES_PASSWORD`** en el archivo local de PostgreSQL, sin guardar secretos en Git. Después inicia Spring Boot:

```bash
npm run dev:api
```

La API escucha por defecto en **http://localhost:8080**. Al arrancar, Flyway aplica las migraciones pendientes a la base configurada.

### 3. Configurar y ejecutar el frontend

En otra terminal:

```bash
cp apps/web/.env.example apps/web/.env.local
npm ci
npm run dev:web
```

El frontend estará disponible en **http://localhost:3000**. Su archivo de entorno local debe contener:

```dotenv
NEXT_PUBLIC_API_URL=http://localhost:8080
```

`NEXT_PUBLIC_API_URL` es visible para el navegador; **nunca almacenes contraseñas o tokens en variables `NEXT_PUBLIC_*`**.

### 4. Comprobar los servicios

```bash
curl -fsS http://localhost:8080/api/health
curl -fsS http://localhost:8080/actuator/health
curl -fsS 'http://localhost:8080/api/products?page=0&size=12'
```

Abre `http://localhost:3000/productos` para comprobar la conexión entre frontend y API. Para detener PostgreSQL conservando sus datos:

```bash
docker compose -f infrastructure/compose.yaml down
```

No añadas `-v` a ese último comando salvo que realmente quieras eliminar el volumen de la base local.

## Variables de entorno

Las variables de la API están definidas en `apps/api/src/main/resources/application.yml`; las siguientes son las principales:

| Variable | Uso |
| --- | --- |
| `DB_URL` | URL JDBC de PostgreSQL; alternativa a `DB_HOST`, `DB_PORT` y `DB_NAME`. |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Componentes de conexión PostgreSQL si no se proporciona `DB_URL`. |
| `DB_USERNAME` | Usuario de PostgreSQL. |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña privada de PostgreSQL. |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos del frontend, separados por comas; usar orígenes exactos. |
| `NEXT_PUBLIC_API_URL` | URL pública HTTPS de la API para el frontend, salvo en desarrollo localhost. |
| `TECHSTORE_PAYMENT_PROVIDER` | `DISABLED` por defecto; `SANDBOX` únicamente en entornos de demostración. |
| `TECHSTORE_PAYMENT_SANDBOX_ENABLED` | Activa expresamente el simulador de pagos; `false` por defecto. |
| `TECHSTORE_PAYMENT_WEBHOOK_ENABLED` | Habilita el webhook del simulador; `false` por defecto. |
| `TECHSTORE_PAYMENT_WEBHOOK_SECRET` | Secreto HMAC externo necesario si se habilita el webhook. |
| `TECHSTORE_SHIPPING_PROVIDER` | `DISABLED` por defecto; `MANUAL` o `SIMULATOR` según el entorno. |
| `CHECKOUT_SHIPPING_POLICY` | `FIXED` o `FREE_OVER_THRESHOLD`. |
| `CHECKOUT_TAX_RATE` | Tasa ilustrativa de impuestos usada en checkout. |

Las configuraciones de ejemplo no deben reutilizarse en entornos públicos. Las credenciales reales se almacenan en el gestor de secretos del proveedor de despliegue y no en el repositorio.

## Despliegue

El frontend y el backend se despliegan **por separado**. El sitio web necesita una API pública y una base PostgreSQL operativa para utilizar las funciones de negocio.

### Backend y PostgreSQL: Render

El archivo [`render.yaml`](render.yaml) contiene una plantilla de Render con:

- `techstore-api-demo`: servicio web Docker que utiliza `apps/api/Dockerfile`, contexto `apps/api` y health check `/actuator/health`.
- `techstore-postgres-demo`: base PostgreSQL 17 de demostración.
- Conexión entre el servicio y la base mediante variables que Render obtiene del recurso PostgreSQL.
- Pagos y proveedor logístico desactivados por defecto.

Para usarla, importa el repositorio como **Blueprint** en Render, selecciona una rama que contenga los archivos actualizados, revisa los recursos y el plan antes de aprobar su creación. **El archivo YAML por sí solo no crea ni demuestra un despliegue activo**.

Como alternativa, puedes construir la imagen del backend manualmente:

```bash
docker build -f apps/api/Dockerfile -t techstore-api:local apps/api
```

El contenedor ejecuta la API como usuario no privilegiado y utiliza el puerto indicado por `PORT` (por defecto `8080`). En un proveedor externo configura la conexión PostgreSQL, secretos, CORS y health check; utiliza almacenamiento persistente y acceso restringido a la base.

> El Blueprint incluido está orientado a una **demo con datos ficticios**, no a una tienda productiva. Revisa los límites vigentes del plan de Render antes de aprovisionar recursos: disponibilidad, suspensión, expiración, copias de seguridad y posibles costos pueden depender del plan.

### Frontend: Vercel

El proyecto Vercel referenciado en la documentación es `techstore-ecommerce`.

1. Conecta el repositorio en Vercel y configura el directorio raíz del frontend como `apps/web`, con Node.js 22.
2. Selecciona una rama de despliegue que contenga la aplicación revisada. El desarrollo se integra en `dev`; **no asumas que `main` ya contiene esa versión**.
3. Define `NEXT_PUBLIC_API_URL` con la **URL HTTPS real del backend**.
4. Compila y despliega el frontend. Si cambia `NEXT_PUBLIC_API_URL`, vuelve a compilar: Next.js incorpora esa variable durante el build.
5. En el backend, configura `CORS_ALLOWED_ORIGINS` con el origen HTTPS exacto del frontend publicado, sin rutas ni comodines.

Sin configurar `NEXT_PUBLIC_API_URL`, la web usa `http://localhost:8080`, que **no funciona para visitantes externos**.

### Comprobación después del despliegue

Sustituye los dominios siguientes por las URL HTTPS reales asignadas por los proveedores:

```bash
curl -fsS https://API_PUBLICA/actuator/health
curl -fsS https://API_PUBLICA/api/health
curl -fsS 'https://API_PUBLICA/api/products?page=0&size=12'
curl -I https://FRONTEND_PUBLICO/
```

Verifica también desde el navegador que el frontend llama a la API pública y no a `localhost`. Las migraciones Flyway se ejecutan al iniciar el backend: utiliza una base dedicada, respalda sus datos y revisa la compatibilidad de las migraciones antes de desplegar una nueva versión.

## Compilación y CI/CD

```bash
npm run lint:web
npm run typecheck:web
npm run validate:api-contract
npm run test:api-contract
npm run build:web
mvn -B -ntp -f apps/api/pom.xml clean verify
```

La prueba `mvn verify` incluye pruebas de integración que requieren una base PostgreSQL aislada `techstore_test` configurada con las variables `DB_TEST_*`. El workflow de [GitHub Actions](.github/workflows/ci.yml) prepara PostgreSQL efímero y ejecuta comprobaciones de frontend, backend y E2E. Se ejecuta en PR y pushes dirigidos a `dev` y `main`.

## Documentación de configuración y despliegue

- [Despliegue detallado y configuración de proveedores](docs/deploy/PRODUCCION.md).
- [Configuración segura de staging](docs/ops/BKL-060_STAGING_READINESS.md).
- [Respaldo y comprobación de salud](docs/ops/BKL-061_BACKUP_AND_HEALTH.md).
- [Manual de operación y diagnóstico](docs/ops/BKL-063_OPERATIONS_MANUAL.md).
- [Contrato OpenAPI](docs/api/openapi.json).

**Nota:** Este repositorio contiene la configuración para ejecutar y desplegar TechStore, pero no constituye prueba de que la API pública, PostgreSQL administrado o el entorno staging estén operativos. Los pagos y la logística de simulación no deben presentarse como servicios reales.
