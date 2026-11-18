# Estructura del monorepositorio

El repositorio contiene dos aplicaciones y una configuración auxiliar de infraestructura:

- `apps/web`: interfaz web Next.js con TypeScript (App Router).
- `apps/api`: backend Spring Boot con Java 21 y Maven.
- `infrastructure`: Docker Compose para PostgreSQL local.
- `docs`: diseño, decisiones y plan de entrega.
- `.github`: configuración futura de CI y revisiones.

## Principios

1. Un repositorio para versionar web, API y documentación de forma coordinada.
2. El frontend usa HTTP/JSON para acceder a la API.
3. El backend será **un monolito modular**, con módulos internos por dominio, no microservicios.
4. No se generan carpetas vacías; los módulos se crearán en sus issues correspondientes.
5. Mantener credenciales reales fuera de Git; almacenar solo `.env.example`.
6. Los tres patrones GoF y MVC requeridos por el Proyecto #3 se implementarán durante las funcionalidades de negocio, no dentro de este issue de inicialización.

## Decisiones de este issue

- NPM workspaces administra solo `apps/web`; Maven administra `apps/api`.
- La API expone `GET /api/health` para comprobar que Spring Boot arranca.
- PostgreSQL se prepara en Compose pero la API **todavía no se conecta a la base**: la integración corresponde a BKL-011.
- Las versiones se fijan para ofrecer un punto de partida repetible; se generará `package-lock.json` mediante `npm install` en una máquina con acceso a npm.
