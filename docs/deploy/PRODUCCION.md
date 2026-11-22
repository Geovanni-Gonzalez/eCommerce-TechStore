# Despliegue de eCommerce TechStore

## Estado y topología

- Repositorio: `Geovanni-Gonzalez/eCommerce-TechStore`.
- Frontend: Next.js 16 en Vercel, proyecto `techstore-ecommerce`, directorio `apps/web`, Node.js 22.
- API: Spring Boot 4 / Java 21, contenedor Docker por configurar en un proveedor que ejecute servicios HTTP persistentes.
- Base: PostgreSQL 17 administrada, separada de cualquier base local o de CI.
- `main` contiene documentación y `dev` contiene la aplicación. Antes de activar despliegues automáticos en producción, configurar la rama de producción de Vercel con una rama de release o fusionar una versión revisada en `main`; no desplegar `main` tal como está.

El despliegue de la web **no habilita por sí solo** catálogo, carrito, autenticación, checkout, reseñas o pedidos: éstos requieren la API operativa. En ausencia de `NEXT_PUBLIC_API_URL`, el frontend utiliza `http://localhost:8080`, que es incorrecto para visitantes externos.

## 1. Contenedor de la API

En la raíz del repositorio:

```bash
docker build -f apps/api/Dockerfile -t techstore-api:local apps/api
```

La imagen compila el JAR con Maven y ejecuta Java 21 como usuario sin privilegios. El proceso escucha en `PORT` del proveedor (o `8080` por defecto).

En la plataforma de ejecución, usar el Dockerfile `apps/api/Dockerfile` y el contexto `apps/api`. Publicar HTTP en el puerto proporcionado por `PORT` y configurar la comprobación de salud `/actuator/health` (o `/api/health`). No usar el archivo `infrastructure/compose.yaml` como infraestructura pública: publica PostgreSQL únicamente en localhost y trae valores de ejemplo.

## 2. Base de datos y variables privadas de la API

Aprovisionar PostgreSQL con almacenamiento persistente, backups y controles de acceso. Configurar en el gestor de secretos del proveedor:

| Clave | Valor esperado |
| --- | --- |
| `DB_URL` | JDBC, por ejemplo `jdbc:postgresql://HOST:5432/DB` (TLS según proveedor) |
| `DB_USERNAME` | Usuario de base dedicado a la aplicación |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña **privada** del usuario |
| `CORS_ALLOWED_ORIGINS` | Origen HTTPS exacto del frontend Vercel (sin barra final) |
| `TECHSTORE_PAYMENT_PROVIDER` | `DISABLED` por defecto |
| `TECHSTORE_PAYMENT_SANDBOX_ENABLED` | `false` por defecto |
| `TECHSTORE_PAYMENT_WEBHOOK_ENABLED` | `false` por defecto |
| `TECHSTORE_SHIPPING_PROVIDER` | `DISABLED` por defecto |

Flyway aplica migraciones al inicio: revisar la compatibilidad de las migraciones y arrancar inicialmente una sola instancia, comprobando la salud y `flyway_schema_history` antes de escalar. Nunca conectar pruebas de CI ni scripts de seed a la base de producción. No subir credenciales, tokens, secretos HMAC o direcciones de clientes al repositorio ni a logs.

**Advertencia:** el sistema usa pago SANDBOX y logística de simulación; no ofrece procesamiento bancario real ni comprobantes de entrega física. No activar esas simulaciones frente a compradores reales como si fueran transacciones auténticas.

## 3. Conectar la web en Vercel

En Vercel → `techstore-ecommerce` → Settings → Environment Variables:

- Establecer `NEXT_PUBLIC_API_URL=https://API_PUBLICA` para el entorno requerido.
- Reconstruir y desplegar la web después de establecer la variable. `NEXT_PUBLIC_*` se incorpora al bundle del navegador durante el build.
- Revisar la rama de producción: actualmente Vercel la considera `main`, pero el código funciona en `dev`. Evitar publicar accidentalmente una rama sin aplicación.
- Evaluar la protección de acceso (Vercel Authentication / SSO) solo después de verificar qué audiencia puede acceder a la demo.
- En la API, poner en `CORS_ALLOWED_ORIGINS` la URL exacta, sin rutas; nunca `*` para sortear errores.

## 4. Validación posterior al despliegue

```bash
curl -fsS https://API_PUBLICA/actuator/health
curl -fsS https://API_PUBLICA/api/health
curl -fsS 'https://API_PUBLICA/api/products?page=0&size=12'
curl -I https://FRONTEND_PUBLICO/
```

Comprobar manualmente una ruta de catálogo y que el frontend no llama a `localhost:8080`. Después ejecutar E2E contra una **base aislada con datos ficticios**, nunca sobre pedidos ni pagos reales. Validar controles de acceso, firmas HMAC y encabezados CORS antes de habilitar cualquier simulación de pagos, y revisar errores de runtime en los logs del proveedor.

## 5. CI/CD y release

`.github/workflows/ci.yml` ejecuta lint, tipos, contrato, build y pruebas de backend/Playwright; exigir su éxito antes de fusionar una rama de release. Vercel está vinculado a GitHub para desplegar desde Git. El backend debe configurarse con despliegues desde una rama de release revisada, usando secretos guardados en el proveedor. Para evitar interrupciones, coordinar migraciones, test de salud, smoke funcional y rollback. El contenedor presentado aquí está **preparado**, no demuestra aún un backend publicado.

## 6. Blueprint Render para demostración

El archivo `render.yaml` del repositorio declara **dos recursos nuevos**, ambos gratuitos, en la región `virginia`:

- `techstore-api-demo`: servicio web Docker desde `apps/api/Dockerfile`, `/actuator/health` como health check.
- `techstore-postgres-demo`: PostgreSQL 17 de demostración `techstore_demo`, acceso externo desactivado mediante `ipAllowList: []`.

La API toma `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` y `SPRING_DATASOURCE_PASSWORD` directamente de `fromDatabase`: **no crear contraseñas en GitHub**. La configuración Spring mantiene compatibilidad con `DB_URL` para entornos existentes.

**El Blueprint no se aplica automáticamente por estar en GitHub.** Debe validarse el YAML, planificarse en Render, revisar el plan y aprobar la creación en el espacio de trabajo correcto; hacerlo crea recursos externos. No fusionar con `dev` sin CI y revisión. Antes de desplegar desde `dev`, cambiar la rama de Blueprint del borrador a `dev` una vez fusionado.

**Limitaciones del plan gratuito**: Render suspende el web service tras 15 minutos sin tráfico (arranque en frío); PostgreSQL gratuito expira a los 30 días, tiene 1 GB de almacenamiento y **no incluye copias de seguridad**. Es apto solo para un prototipo académico con datos ficticios, nunca para pedidos o credenciales reales. Render también puede facturar uso extra de ancho de banda/minutos de compilación si hay un método de pago configurado. Revisar los límites en https://render.com/docs/free antes de aprobar.

Tras desplegar la API, asignar su URL HTTPS **real** a `NEXT_PUBLIC_API_URL` en Vercel para producción y vistas previas necesarias, y volver a compilar Next.js. Verificar el dominio de frontend exacto contra `CORS_ALLOWED_ORIGINS`; no ampliar CORS a `*`.
