# BKL-057 — Medición p95 de catálogo y carrito

El benchmark de solo lectura se encuentra en
`scripts/qa/load_catalog_cart.py`. Ejecuta **GET** de catálogo público
y carrito autenticado con un cliente de pruebas, sin modificar pedidos ni
stock. La configuración limita 300 solicitudes por endpoint y 8 hilos
como máximo, exige localhost y rechaza credenciales en la URL.

## Preparación local obligatoria

1. Arrancar **PostgreSQL aislado** y la API con datos de demostración.
2. Registrar un CLIENT de pruebas e iniciar sesión por API, usando una
   cuenta que NO contenga datos personales reales.
3. Guardar el Bearer únicamente en una variable de entorno temporal,
   `TECHSTORE_LOAD_BEARER_TOKEN`. No imprimirlo ni versionarlo.
4. Confirmar que la API apunta a una base de TEST mediante
   `TECHSTORE_LOAD_TEST_DB_CONFIRMED=yes`.
5. Ejecutar:
   `python3 scripts/qa/load_catalog_cart.py --requests 100 --concurrency 4`

Los objetivos iniciales (ajustables) son **p95 catálogo 500 ms** y
**p95 carrito 700 ms** en entorno local. No son garantías de producción.
La salida informa muestras, fallos HTTP, p50 y p95 sin guardar cuerpos,
tokens, usuarios ni direcciones. El comando sale 1 si hay fallos o se
supera cualquiera de los presupuestos.

## Evidencia para completar el issue

Registrar fecha, commit, sistema operativo, recursos de CPU/RAM, latencia
de red local, tamaño del catálogo, número de solicitudes, concurrencia,
p50/p95 y errores, más el cuello de botella observado. Repetir al menos
tres veces en entorno estable y comparar variabilidad. Para la CI solo se
corrobora la seguridad y el cálculo del harness; la prueba de carga
operativa es manual hasta contar con un entorno controlado.

Prueba local sin red: `python3 -m unittest scripts.qa.test_load_catalog_cart`.
