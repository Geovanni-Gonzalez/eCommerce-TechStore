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

## Estado del proyecto

Los issues **BKL-008 y BKL-011** preparan el monorepositorio, PostgreSQL, JDBC y las migraciones. **BKL-010** añade módulos de backend, Actuator y CORS local.

**No implementa todavía:** registro de usuarios, catálogo conectado a la API, carrito, pedidos ni pagos; tampoco los patrones GoF. Cada funcionalidad se construirá en su issue, sin prometer una tienda operativa al completar el esqueleto.

La entrega del curso requiere MVC, al menos tres patrones GoF (Strategy, Facade y Observer propuestos), exposición con UX/UI/accesibilidad y **repositorio público**. Antes de cambiar la visibilidad debe revisarse que no existan secretos.

Consulta [documentación de arquitectura](docs/architecture/MONOREPO.md), [plan de entrega](docs/PLAN_ENTREGA_19_NOV_2026.md) y [GitHub Issues](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues).
