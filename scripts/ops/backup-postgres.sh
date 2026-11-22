#!/usr/bin/env bash
# BKL-061: respaldo local de DB TEST/STAGING. Nunca usar contra producción.
set -euo pipefail
umask 077
: "${PGHOST:?Configura PGHOST}"
: "${PGPORT:?Configura PGPORT}"
: "${PGUSER:?Configura PGUSER}"
: "${PGDATABASE:?Configura PGDATABASE}"
: "${PGPASSWORD:?Configura PGPASSWORD sin imprimirlo}"
: "${BACKUP_DIR:?Configura BACKUP_DIR seguro}"

if [[ "${TECHSTORE_BACKUP_DB_ACK:-}" != "yes" ]]; then
  echo "Confirma explícitamente base de prueba: TECHSTORE_BACKUP_DB_ACK=yes" >&2
  exit 2
fi
if [[ "$PGDATABASE" != "techstore_test" && "$PGDATABASE" != "techstore_staging" ]]; then
  echo "Solo se permite techstore_test o techstore_staging; no producción." >&2
  exit 2
fi
if [[ ! -d "$BACKUP_DIR" || -L "$BACKUP_DIR" ]]; then
  echo "BACKUP_DIR debe existir y no puede ser symlink." >&2
  exit 2
fi
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
output="$BACKUP_DIR/$PGDATABASE-$stamp.dump"
partial="$output.partial"
trap 'rm -f -- "$partial"' EXIT
pg_dump --format=custom --no-owner --no-acl --file="$partial"
pg_restore --list "$partial" >/dev/null
mv -n -- "$partial" "$output"
trap - EXIT
chmod 600 "$output"
echo "Respaldo creado y estructura verificada: $output"
