# BKL-063 — Manual de operación y diagnóstico de TechStore

## Alcance y restricciones

TechStore es un **prototipo académico** B2C, no una pasarela de cobro real.
El stack consiste en Next.js/TypeScript (`apps/web`), Spring Boot
Java 21 (`apps/api`) y PostgreSQL 17. La aplicación usa patrones
GoF Strategy (envío), Facade (checkout) y Observer (auditoría de pedido).
Los pagos de sandbox y los eventos de entrega simulados no acreditan
transacciones bancarias ni transporte físico.

## Arranque local reproducible

1. Verificar Node 22, npm, Java 21, Maven 3.9+, Docker y Compose.
2. Copiar `infrastructure/.env.example` a `infrastructure/.env` y
   revisar sus valores **solo para desarrollo local**. No versionar el real.
3. `docker compose -f infrastructure/compose.yaml up -d`.
4. Confirmar `docker compose -f infrastructure/compose.yaml ps`.
5. Proporcionar `SPRING_DATASOURCE_PASSWORD` a la terminal de Maven
   sin insertarla en URL ni en Git; ejecutar `npm run dev:api`.
6. Desde otra terminal ejecutar `npm ci` y `npm run dev:web`.
7. Consultar `http://localhost:8080/api/health` y
   `http://localhost:8080/actuator/health`, y abrir
   `http://localhost:3000/productos`.

`NEXT_PUBLIC_API_URL` de `apps/web/.env.example` apunta a la API;
su configuración local real no debe contener datos secretos. El prefijo
`NEXT_PUBLIC_` **expone** el valor en el navegador, por lo que allí solo
deben existir URLs públicas.

## Validación de cambios

```bash
npm run lint:web
npm run typecheck:web
npm run validate:api-contract
npm run test:api-contract
npm run build:web
mvn -B -ntp -f apps/api/pom.xml clean verify
```

Los tests `*IT.java` requieren `techstore_test` PostgreSQL aislado con
variables DB_TEST_; no deben apuntar a `techstore` compartida. En CI
se usan servicios PostgreSQL efímeros y Chromium en escenarios sandbox
`APPROVED` y `DECLINED`.

## Módulos y diagnóstico

| Síntoma | Inspección segura | Respuesta |
| --- | --- | --- |
| API no arranca | `docker compose ... ps`, Java 21, credencial DB local y `flyway_schema_history` | Corregir entorno, conservar volumen; no borrar DB por defecto |
| Next.js no conecta | Revisar `NEXT_PUBLIC_API_URL`, CORS y URL HTTP(s) local | Corregir origen permitido, no habilitar `*` indiscriminadamente |
| Registro/login falla | Respuesta HTTP 400/401/403, fecha de sesión, estado de DB | No registrar contraseña ni Bearer en tickets o capturas |
| Carrito/checkout falla | 409 stock, carrito, cotización del servidor | Actualizar carrito; nunca crear reservas manuales por interfaz |
| Pedido queda PENDING | Revisar pago sandbox, eventos firmados y conciliación | No marcar CONFIRMED sin evidencia verificada |
| Envío SIMULATOR figura DELIVERED | Revisar `fulfillment_method` y log de envíos | No interpretarlo como entrega real ni habilitar reseñas |
| Reseña no permitida | Revisar elegibilidad del endpoint privado | Requiere compra CONFIRMED y entrega MANUAL auditada |
| Salud no disponible | `bash scripts/ops/check-health.sh` | Escalar a responsable; verificar DB y API antes de reiniciar |

## Restauración y reversión

En un entorno **no productivo**, usar las instrucciones de
`docs/ops/BKL-061_BACKUP_AND_HEALTH.md`: respaldo `pg_dump` con
permisos 0600 y ensayo de `pg_restore` sobre una DB nueva y vacía.
La verificación de cabeceras del dump no sustituye restauración total.
Las migraciones Flyway ejecutadas deben tratarse como **inmutables**.
Para un cambio de aplicación fallido, volver al despliegue anterior
solo después de comprobar compatibilidad de la base de datos y salud.

## Variables, secretos y evidencia

- Variables de entorno reales nunca en Git, PR, capturas, URL o navegador.
- `TECHSTORE_PAYMENT_PROVIDER=SANDBOX` y toggles de sandbox son
  configuración de demostración, no un modo de pagos reales.
- Rotar y revocar credenciales filtradas antes de limpiar historial.
- Revisión de secretos: `docs/security/BKL-070_SECRET_REVIEW.md`
  (se integra de manera independiente).
- API/REST: `docs/api/openapi.json` y `docs/api/REST_CONVENTIONS.md`.
- Arquitectura/GoF: `docs/architecture/BKL-065_MVC_DEMO.md`
  (se integra de manera independiente).
- Auditoría: `docs/backend/ORDER_OBSERVER.md`.

## Aún obligatorio para cerrar el bloque

Documentar un despliegue staging real (URL y procedimiento, **sin secretos**),
responsables, alertas, política de respaldo cifrado, ensayo de restauración,
revisión cruzada y evidencia operativa. Este manual no demuestra que
staging ni los respaldos remotos estén operativos.
