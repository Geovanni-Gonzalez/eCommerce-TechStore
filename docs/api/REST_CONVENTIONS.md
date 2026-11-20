# BKL-013 — Convenciones REST y contrato OpenAPI

**Fuente de verdad:** [openapi.json](openapi.json), formato **OpenAPI 3.0.3**. La especificación es un **contrato preliminar de desarrollo**, no una afirmación de que ya existan controladores para todas las rutas.

**Fecha de entrega actualizada:** sábado 21 de noviembre de 2026, 10:00 p. m. (Costa Rica).

## Estado y responsabilidades

- **Implementado:** `GET /api/health` (BKL-008/BKL-010), `GET /api/products` y `GET /api/products/{id}` (BKL-018), `POST /api/auth/register` (BKL-015), `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` y `GET /api/admin/check` (BKL-016). CI comprueba catálogo, registro y sesiones Bearer sobre PostgreSQL real. `GET /actuator/health` es propio de Spring Actuator.
- **Implementado en BKL-028:** carrito de cliente autenticado (`GET/DELETE /api/cart`, `POST /api/cart/items`, `PATCH/DELETE /api/cart/items/{productId}`), respaldado por PostgreSQL. **La vista web del carrito sigue pendiente** (BKL-029).
- **Planificado:** pedidos (BKL-030/BKL-033 y posteriores), panel administrativo completo, autenticación de la interfaz web. `/api/admin/check` solo demuestra autorización por rol.
- El atributo OpenAPI `x-implementation-status: planned | implemented` distingue ambos estados de forma explícita. Documentar **no** habilita un endpoint.
- **Frontend:** consume estas rutas por HTTP/JSON usando `apps/web/src/lib/api.ts` y `NEXT_PUBLIC_API_URL`.
- **Backend:** implementa DTOs, controladores, validaciones, persistencia y seguridad en sus issues. Actualizar el contrato y las pruebas en el mismo PR que cambie una ruta.

## Convenciones HTTP

1. Prefijo `/api`; recursos en plural y minúsculas; identificadores numéricos en `/{id}`. No colocar verbos ni datos sensibles en la URL.
2. `GET` solo consulta, `POST` crea o inicia operaciones; escrituras posteriores podrán usar `PUT`, `PATCH` o `DELETE` cuando sea apropiado.
3. Intercambio mediante `application/json`, nombres de propiedades en **camelCase**, fechas en ISO 8601 con zona, montos en **CRC** y sin datos de tarjeta.
4. `200 OK` lectura exitosa; `201 Created` alta o pedido creado; `400 Bad Request` formato o validación de entrada; `401 Unauthorized` sin autenticación; `403 Forbidden` sin permiso; `404 Not Found` recurso inexistente o no accesible; `409 Conflict` conflicto de estado; `422 Unprocessable Entity` regla de negocio incumplida; `500 Internal Server Error` fallo inesperado.
5. Listados paginados: `page` (desde 0), `size` (1–100, 12 por defecto) y respuesta `{items, page, size, totalElements, totalPages}`. Los productos públicos deben tener `active = true`.
6. En el servidor se calcula el precio definitivo del pedido (subtotal, impuestos y envío). El cliente envía `productId` y `quantity`, **nunca** un total confiable.
   El carrito muestra un subtotal estimado de todos sus artículos (incluidos los que ya no están disponibles); no reserva stock y `checkoutReady` indica si las líneas cumplen disponibilidad al consultar. El checkout debe **recalcular y validar**.
7. Se implementó autenticación con token **Bearer opaco** revocable (`bearerFormat: Opaque`, no JWT) para rutas privadas y `/api/admin/**`; falta la futura verificación de propiedad de los pedidos cuando se implementen. El token vive 8 horas y solo se almacena su hash SHA-256 en PostgreSQL.

## Errores uniformes

Respuestas de error como `application/problem+json` siguiendo **Problem Details**, con campos `type`, `title`, `status`; opcionalmente `detail`, `instance`, `code` y `errors: [{field, message}]`. Evitar trazas técnicas o contraseñas en respuestas.

Ejemplo ilustrativo de un `400` con validaciones de campos (el registro devuelve este tipo de respuesta):

```json
{
  "type": "about:blank",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "La solicitud contiene errores de validación.",
  "code": "VALIDATION_ERROR",
  "errors": [
    { "field": "quantity", "message": "Debe ser mayor que cero" }
  ]
}
```

## Seguridad y alcance académico

- Prototipo local; no exponer tokens ni contraseñas en logs o control de versiones. Contraseñas deberán almacenarse como hashes seguros.
- El pago será **SIMULATED**: nunca enviar números de tarjeta, CVV ni datos bancarios.
- El registro seguro está implementado en BKL-015 y la autenticación, revocación y control por roles en BKL-016. Se usa Bearer opaco (no JWT) y no se envían cookies; para producción siguen pendientes HTTPS obligatorio, rate limiting y protecciones adicionales.
- Campos `imageUrl` y `category` en `Product` son opcionales; todavía no se implementa almacenamiento de imágenes.
- El modelo SQL actual usa `price_crc`, `stock`, `active` y `category_id` en `products`; los DTOs del contrato añaden nombres camelCase y datos derivados. No deben interpretarse como migraciones ya aplicadas.

## Validación reproducible

Desde la raíz del repositorio, con Node.js 22:

```powershell
npm run validate:api-contract
npm run test:api-contract
```

El primer comando parsea JSON y verifica referencias locales, operationIds, parámetros de ruta, códigos de respuesta, formato Problem Details, seguridad de pedidos y el estado planificado. El segundo ejecuta pruebas positivas y negativas del validador. Estos controles **no sustituyen** una validación exhaustiva contra el esquema oficial OpenAPI ni pruebas de integración HTTP: se incorporarán al ampliar el sistema.

Para inspeccionar visualmente el contrato se puede importar `docs/api/openapi.json` en un editor OpenAPI; no se requiere servicio externo ni credenciales.

### Política de cambios

Cada nuevo endpoint debe incluir ruta/método, `operationId` único, esquemas de solicitud/respuesta, códigos de error y seguridad, y debe pasar los comandos de validación. Marcar `x-implementation-status: implemented` **solo después** de haber probado el controlador real.
