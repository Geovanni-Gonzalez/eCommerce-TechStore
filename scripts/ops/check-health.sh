#!/usr/bin/env bash
# BKL-061: apto para cron/monitor local; no incluye credenciales.
set -euo pipefail
url="${TECHSTORE_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
if [[ ! "$url" =~ ^https://[^[:space:]]+/actuator/health$ &&
      ! "$url" =~ ^http://(127\.0\.0\.1|localhost)(:[0-9]+)?/actuator/health$ ]]; then
  echo "Health URL debe usar HTTPS o HTTP localhost en /actuator/health" >&2
  exit 2
fi
if curl --fail --silent --show-error --max-time 8 "$url" |
    python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status")=="UP" else 1)'; then
  echo "TechStore health: UP"
else
  echo "TechStore health: NO DISPONIBLE; requiere revisión operativa" >&2
  exit 1
fi
