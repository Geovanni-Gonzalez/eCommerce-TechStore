# ADR — Recuperación y comprobaciones operativas del prototipo

**Estado:** propuesta documentada (BKL-063, pendiente de validación operativa).

## Contexto

La aplicación usa Spring Boot, Next.js, PostgreSQL y Flyway, con pruebas
en servicios efímeros de CI. Su demo de pagos sandbox no debe
interpretarse como disponibilidad bancaria ni comportamiento en producción.
Se necesita un plan de fallo que no comprometa credenciales ni datos reales.

## Decisión

Mantener las migraciones Flyway ya aplicadas **inmutables** y separar
las pruebas de recuperación en la DB `techstore_test` o
`techstore_staging`, nunca sobre un entorno compartido. Registrar
`/actuator/health` como señal inicial de salud, respaldos locales
restringidos mediante `pg_dump --format=custom` y un ensayo
**independiente** de restauración completo antes de dar por cerrado BKL-061.

En desarrollo usar `npm ci`, Java 21 y el contrato REST validado en
CI. No activar pagos ni logística reales a partir de flags de sandbox.
No almacenar secretos en Git; exponer únicamente la URL de la API
en `NEXT_PUBLIC_API_URL`.

## Alternativas consideradas

- Ejecutar `pg_dump` directamente contra cualquier DB: rechazado por
  riesgo de fuga y operación accidental sobre producción.
- Confiar únicamente en `pg_restore --list`: rechazado como garantía
  de restaurabilidad; sirve solo de comprobación estructural básica.
- Usar `/api/health` para inferir disponibilidad total: insuficiente;
  se requiere observar salud de dependencias y verificaciones externas.

## Consecuencias y validación

Los scripts locales son comprobables sin un proveedor de nube, pero la
solución no ofrece todavía backup remoto cifrado, alertas, RTO/RPO
medidos ni despliegue staging. Registrar esos resultados en BKL-060/061
y no declararlos Done por documentación solamente.
