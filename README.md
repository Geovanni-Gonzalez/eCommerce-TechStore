# eCommerce TechStore

Prototipo académico de tienda B2C de accesorios tecnológicos para el Proyecto #3 de Diseño de Software. Se trabajará con arquitectura monolítica modular, frontend y backend separados.

**Entrega académica:** 19 de noviembre de 2026, 4:00 p. m. (Costa Rica).

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

> El archivo `package-lock.json` se deberá generar y versionar al ejecutar `npm install` en un equipo con acceso a npm. La rama de inicialización contiene dependencias declaradas, pero no un lockfile generado.

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

> No se necesita PostgreSQL para el endpoint de salud inicial. La conexión real de la API a la base de datos pertenece a BKL-011.

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

## Estado del proyecto

Este issue **BKL-008** crea el monorepositorio y permite iniciar por separado la interfaz y una API mínima de salud.

**No implementa todavía:** registro de usuarios, catálogo conectado a la API, carrito, pedidos, persistencia PostgreSQL, pagos ni los patrones GoF. Cada funcionalidad se construirá en su issue, sin prometer una tienda operativa al completar el esqueleto.

La entrega del curso requiere MVC, al menos tres patrones GoF (Strategy, Facade y Observer propuestos), exposición con UX/UI/accesibilidad y **repositorio público**. Antes de cambiar la visibilidad debe revisarse que no existan secretos.

Consulta [documentación de arquitectura](docs/architecture/MONOREPO.md), [plan de entrega](docs/PLAN_ENTREGA_19_NOV_2026.md) y [GitHub Issues](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues).
