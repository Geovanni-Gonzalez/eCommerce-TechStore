# BKL-060 — Preparación de staging seguro

## Estado de este PR

Se entrega un **validador sin red** y una guía de configuración.
**No se creó ni desplegó un entorno staging**. La infraestructura, dominio,
certificado TLS, base PostgreSQL aislada y secretos del proveedor requieren
configuración real. No se solicita ni almacena ningún secreto en Git.

`node scripts/ops/validate-staging-config.mjs` comprueba de forma defensiva
variables del proceso, mostrando únicamente nombres de controles incumplidos,
no valores. `node --test scripts/ops/validate-staging-config.test.mjs`
ejecuta fixtures ficticios en CI.

## Variables necesarias (no copiarlas con valores reales al repositorio)

| Variable | Política |
| --- | --- |
| `STAGING_WEB_URL` | Origen HTTPS del frontend |
| `STAGING_API_URL` | Origen HTTPS de la API |
| `NEXT_PUBLIC_API_URL` | Solo el origen HTTPS de API, es **público** en browser |
| `CORS_ALLOWED_ORIGINS` | Solo origen de web staging, sin wildcard |
| `DB_URL` | JDBC PostgreSQL remoto, base exclusiva staging, no test/local |
| `DB_USERNAME` | Identidad con privilegios mínimos |
| `SPRING_DATASOURCE_PASSWORD` | Secreto externo seguro, nunca en .env versionado |
| `TECHSTORE_PAYMENT_PROVIDER` | `DISABLED` o `SANDBOX`; jamás pasarela de dinero real |
| `TECHSTORE_PAYMENT_WEBHOOK_ENABLED` | `false` hasta verificación independiente |
| `TECHSTORE_PAYMENT_SANDBOX_AUTO_CONFIRM` | `false` hasta verificación independiente |

Las URL `example.test` de tests son ficticias y no representan un despliegue.

## Lista de aceptación operacional

- [ ] Crear entorno HTTPS y configurar TLS con proveedor real.
- [ ] Crear base de datos vacía aislada con backups cifrados, usuario mínimo
      y configuración segura de PostgreSQL.
- [ ] Configurar secretos solo en secretos cifrados del proveedor.
- [ ] Arrancar API y web sin cambiar el modo seguro de pagos.
- [ ] Verificar `GET /api/health`, `GET /actuator/health`, catálogo,
      registro, autenticación y consulta de pedidos.
- [ ] Ejecutar compra **solo sandbox** y demostrar que no hay cobro real;
      verificar 401/403/404 y que una URL no determine estado financiero.
- [ ] Auditar logs, CORS, TLS, headers, ausencia de PII y permisos.
- [ ] Documentar procedimiento de rollback compatible con Flyway y respaldos.
- [ ] Revisión cruzada y adjuntar evidencia de la URL staging operativa
      sin tokens ni datos privados.

BKL-062 y BKL-064 no deben darse por completados hasta ejecutar estos pasos.
