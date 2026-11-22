# BKL-053 — Notificaciones transaccionales desacopladas

## Alcance implementado

Al confirmar **la creación** de un pedido, el `CheckoutFacade` publica
`OrderCreatedEvent`; `OrderNotificationListener` lo observa solo
`AFTER_COMMIT`. Se registra un aviso **in-app** idempotente en
`order_notifications` (Flyway V24), sin correo, dirección, teléfono ni
datos sensibles. El aviso informa que se **recibió** el pedido y que se
puede consultar su estado; NO afirma aprobación del pago. La confirmación
de pago sigue siendo exclusiva de la conciliación confiable BKL-040.

`GET /api/orders/{orderId}/notifications` requiere token Bearer CLIENT.
Primero comprueba propiedad del pedido y vuelve a filtrar por propietario en
SQL; un cliente ajeno recibe 404 y nunca consulta sus notificaciones.

## Diseño y garantías

- Listener desacoplado de `CheckoutFacade` mediante el Observer existente.
- Persistencia `REQUIRES_NEW` posterior al commit y clave única por pedido
  y tipo de evento; un retry no duplica el aviso.
- `OrderNotificationChannel` es un puerto para futuros canales (correo);
  `DisabledEmailNotificationChannel` no envía ni afirma envío.
- Fallo del canal externo o de la persistencia se registra sin revertir el
  pedido ya confirmado. Es `best effort`, no un transactional outbox;
  un crash entre commit y listener puede perder el aviso.
- Mensaje local no constituye prueba de pago, entrega ni email enviado.

## Pruebas

`mvn -B -ntp -f apps/api/pom.xml clean verify`

`OrderNotificationListenerTest` comprueba que fallo de canal SMTP simulado o
de persistencia posterior al commit no lanzan una excepción a checkout.
La migración debe validarse contra PostgreSQL efímero en CI. Antes de dar
el bloque por Done ejecutar prueba HTTP de rol CLIENT, dueño/ajeno y
repetición del evento, además de revisión cruzada.
