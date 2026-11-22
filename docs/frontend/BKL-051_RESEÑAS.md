# BKL-051 — Formulario y visualización de reseñas

Issue: https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/52

## Integración

La ficha de producto `/productos/{id}` muestra hasta 50 reseñas activas recientes
con calificación, comentario y fecha, sin nombres, correos, identificadores de
cuentas, pedidos ni información de entrega.

- `GET /api/reviews/products/{productId}`: listado público ordenado por fecha;
  404 si el producto no existe. El servidor selecciona únicamente
  `rating`, `comment` y `created_at` de reseñas activas.
- `GET /api/reviews/products/{productId}/eligibility`: requiere sesión CLIENT.
  El frontend no decide elegibilidad mediante estados de pedidos mostrados.
- `POST /api/reviews/products/{productId}`: requiere CLIENT y revalida en
  servidor la entrega MANUAL auditada y la unicidad por cliente/producto.
  La respuesta 409 solicita actualizar la elegibilidad.

## Accesibilidad y validación

Se utilizan controles de radio con etiquetas numéricas 1–5, `fieldset` y
`legend`, textarea etiquetada, contador de caracteres, errores asociados
mediante `aria-describedby`, `role="alert"` y confirmación
`role="status"`. El foco tiene indicador visible, el estado no depende
solo del color y el listado utiliza elementos semánticos y fechas `time`.

El comentario requiere de 5 a 1000 caracteres exactos, sin espacios
iniciales/finales ni caracteres de control; la API sigue siendo la autoridad.
El texto se renderiza como contenido React, nunca como HTML sin filtrar.

## Pruebas manuales pendientes de ejecutar

1. Visitante: puede leer reseñas, pero ve invitación a iniciar sesión.
2. ADMIN: puede leer y no puede publicar.
3. CLIENT sin entrega MANUAL verificada: formulario oculto.
4. CLIENT con entrega MANUAL verificada: califica 1–5, escribe comentario válido y publica.
5. CLIENT con reseña activa: aparece aviso, no segundo formulario.
6. Errores: API desconectada, sesión 401, 409 por carrera, comentario corto/largo,
   teclado, lector de pantalla y móvil.
7. Verificar ausencia de PII en el GET público y que una entrega SIMULATOR
   no habilita el formulario.

## Dependencia de integración

Esta rama parte de `50-bkl-050-delivered-order-review-eligibility`, que todavía
debe integrarse en `dev`. La revisión cruzada, las pruebas de CI y la
validación manual determinan si BKL-051 cumple la Definition of Done.
