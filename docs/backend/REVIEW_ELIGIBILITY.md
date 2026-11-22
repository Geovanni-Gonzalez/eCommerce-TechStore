# BKL-050 — Elegibilidad de reseña por compra y entrega real

Issue: https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/51

## Regla de negocio

Un CLIENT puede publicar una única reseña activa de un producto cuando
cumple **todas** las siguientes condiciones:

1. Su usuario procede del `SessionPrincipal` autenticado (nunca del body).
2. Existe un pedido suyo con `orders.status='CONFIRMED'`.
3. El pedido contiene el producto, demostrado en el snapshot `order_items.product_id`.
4. Ese pedido tiene `shipments.status='DELIVERED'` y
   `shipments.fulfillment_method='MANUAL'` (no SIMULATOR).
5. La bitácora contiene la transición `IN_TRANSIT→DELIVERED` de razón
   `MANUAL_DELIVERY` (V21/V22).
6. No existe otra reseña `active=true` del mismo `user_id,product_id`.

**Importante:** una compra sandbox confirmada no es suficiente; tampoco
una entrega **simulada**. El estado `SIMULATOR/DELIVERED` de BKL-052 es
una demostración ficticia sin evidencia de recepción física y queda excluido.

## Persistencia

Flyway `V23__delivered_purchase_product_reviews.sql` crea
`product_reviews` con clave de producto, usuario, puntuación 1–5,
comentario de 5–1000 caracteres, indicador `active` y fecha.
Se añade **índice UNIQUE parcial `(user_id,product_id) WHERE active`**
para bloquear duplicados incluso ante solicitudes concurrentes.

Un trigger adicional revalida en PostgreSQL la compra y entrega MANUAL
auditada en cada INSERT/UPDATE activo: no basta el chequeo de servicio.
Las migraciones anteriores permanecen inmutables.

No hay API para desactivar, editar o moderar reseñas en BKL-050.
La columna `active` prepara futuras políticas de moderación sin
permitir dos reseñas activas del mismo producto y CLIENT.

## Endpoints (Bearer ROLE_CLIENT)

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/reviews/products/{productId}/eligibility` | Elegibilidad, indicador de reseña previa y motivo |
| GET | `/api/reviews/products/{productId}/mine` | Reseña propia activa o 404 |
| POST | `/api/reviews/products/{productId}` | Crear reseña propia, 201; 409 si no elegible o duplicada |

Ejemplo de POST:

```json
{"rating":5,"comment":"El accesorio funciona según lo esperado."}
```

Solo se aceptan `rating` entero entre 1 y 5 y `comment` limpio entre
5 y 1000 caracteres. Se rechazan propiedades extra (como `userId`,
`orderId`, `active`), texto de control y valores decimales.
El usuario no puede elegir qué pedido lo habilita ni a quién pertenece
la reseña. Respuestas privadas `Cache-Control: no-store`, sin
correo, direcciones, comprador, actor de entrega ni identificadores de
pedidos. Errores en Problem Details: 400 inválido, 401/403 roles,
404 producto ausente/ninguna reseña propia y 409 no elegible/duplicada.

`GET eligibility` devuelve solo `ELIGIBLE`, `NOT_DELIVERED` o
`ALREADY_REVIEWED`, y `eligible`/ `hasActiveReview`.

## BKL-051

El formulario accesible y la lista de reseñas del catálogo **no
forman parte de BKL-050**. BKL-051 utilizará estos endpoints y
definirá cómo mostrar o moderar públicamente las reseñas; no
debe habilitar el formulario por un `DELIVERED` simulado.

## QA integral pendiente

Mantener la CI de la PR. Para la fase final comprobar:
CLIENT propio/ajeno, sin sesión/ADMIN; entrega sin compra del producto;
pedido confirmado pero sin entrega; entrega MANUAL auditada;
SIMULATOR DELIVERED; error/concurrencia de doble POST; restricción
UNIQUE parcial; trigger DB directo; validación rating/texto;
ausencia de PII, no regresión de pedidos/pagos/stock.
