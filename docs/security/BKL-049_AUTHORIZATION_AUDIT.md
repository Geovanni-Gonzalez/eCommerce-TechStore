# BKL-049 — Auditoría de autorización CLIENT / ADMIN

Issue: https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/50

## Matriz de riesgos y controles

| Recurso | Amenaza | Control verificado en código | Prueba HTTP |
| --- | --- | --- | --- |
| `GET /api/orders/{id}` | IDOR: cliente lee pedido ajeno | `OrderRepository.findByIdForUser(userId,orderId)` + 404 indistinguible | `OrderQueryHttpIT.crossUserIdorIsIndistinguishableFromMissingOrder` |
| `GET /api/orders/{id}/history` | Exposición horizontal de historial/actor | owner check antes de historial; DTO sin actorId | `OrderQueryHttpIT.adminCanInspectAllOrdersWhileClientNeverSeesActorIds` |
| `GET /api/orders/{id}/shipping` | Lectura de entregas ajenas | `ShippingCustomerService` aplica owner en SQL y devuelve 404 | `OrderQueryHttpIT.bkl049VerticalAndHorizontalAccessToShippingAndReviewsFailsClosed` |
| `GET /api/admin/orders` | Escalación CLIENT a ADMIN | `ApiSecurityConfig` exige ROLE_ADMIN | `OrderQueryHttpIT.onlyBearerClientCanReadTheirRoutesAndOnlyAdminCanReadAdminRoutes` |
| `POST /api/reviews/products/{id}` | ADMIN/visitante publica reseñas | ROLE_CLIENT, identidad desde `SessionPrincipal`, elegibilidad en DB | `OrderQueryHttpIT.bkl049VerticalAndHorizontalAccessToShippingAndReviewsFailsClosed` |
| `GET /api/reviews/products/{id}/eligibility` | Divulgación de elegibilidad | ROLE_CLIENT; solo el usuario actual | `OrderQueryHttpIT.bkl049VerticalAndHorizontalAccessToShippingAndReviewsFailsClosed` |
| `GET /api/reviews/products/{id}` | Confusión público/privado | Proyección pública sin `userId` ni pedido | `ProductReviewPublicTest` y prueba HTTP de listado |
| `POST /api/payments/webhooks/sandbox` | Evento falso | HMAC, timestamp, idempotencia del módulo BKL-038 | BKL-056 debe completar auditoría adversarial |

## Casos de aceptación

- Sin token: rutas CLIENT/ADMIN devuelven 401.
- Con rol incorrecto: 403 sin datos del dominio.
- Pedido propio: 200 y `Cache-Control: no-store`.
- Pedido ajeno e inexistente: 404 de seguimiento y detalle, sin revelar existencia.
- Lectura pública de reseñas no implica permiso para escribir.
- Las rutas de administración no usan el `userId` suministrado por el cliente.

## Límites y pendientes

Esta auditoría revisa en código rutas específicas y añade regresión HTTP.
No es un pentest formal ni implica haber probado todas las combinaciones
de clientes, carreras o endpoints. Ejecutar `mvn -B -ntp -f apps/api/pom.xml
verify` con `techstore_test` aislado y obtener aprobación cruzada antes
de cerrar el issue. BKL-056 continúa con webhook firmado, tampering y replay.
No introducir tokens, contraseñas ni PII en evidencias.
