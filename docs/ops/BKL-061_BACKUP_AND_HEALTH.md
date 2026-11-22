# BKL-061 — Respaldo y comprobación de salud (preparación)

## Entregado en el repositorio

- `scripts/ops/check-health.sh`: consulta `/actuator/health` y
  exige `status: UP`. Solo acepta HTTPS o HTTP en localhost, tiempos
  máximos y salida sin secretos. Un error devuelve exit 1 para integrarlo
  a cron/monitor externo; **no configura alertas automáticamente**.
- `scripts/ops/backup-postgres.sh`: `pg_dump` custom de
  `techstore_test` o `techstore_staging`, verificación básica
  `pg_restore --list`, permisos de fichero 0600 y archivo parcial
  eliminado en caso de fallo.

Ninguno de los scripts se ejecuta automáticamente en CI contra una base
real. Solo se revisa la sintaxis Bash.

## Ejecución con DB aislada

1. Confirmar que `PGHOST`, `PGPORT`, `PGUSER`, `PGDATABASE`
   y `PGPASSWORD` apuntan a una DB **de pruebas**. Usar cuentas sin
   privilegios de administración general.
2. Crear un directorio de backups con acceso restringido (0700), fuera
   del repositorio y fuera de carpetas públicas.
3. Definir `BACKUP_DIR` y `TECHSTORE_BACKUP_DB_ACK=yes` para
   reconocer explícitamente el alcance no productivo.
4. Ejecutar `bash scripts/ops/backup-postgres.sh`.
5. Inspeccionar integridad de estructura con `pg_restore --list archivo.dump`
   y **hacer ensayo de restauración** en una base vacía de pruebas,
   creada específicamente para ello. Verificar tablas y registros.
6. Eliminar el entorno de ensayo solo tras confirmar su identidad;
   no ejecutar `dropdb` contra una base existente o compartida.
7. Programar rotación, cifra en reposo y protección de acceso en el
   proveedor elegido; nunca subir el respaldo a Git.

Comprobar salud de API local:

`bash scripts/ops/check-health.sh`

## Pendientes reales

- [ ] Un staging operativo con HTTPS y DB sin datos sensibles (BKL-060).
- [ ] Probar restauración completa, no solo listado del archivo.
- [ ] Destino externo cifrado, retención, alerta del monitor y responsable.
- [ ] Probar fallo de salud y recepción de alerta.
- [ ] CI, revisión cruzada, evidencia con fecha y configuración sin secretos.

Por tanto, los scripts constituyen **preparación del bloque**, no
un sistema de backups/alertas desplegado.
