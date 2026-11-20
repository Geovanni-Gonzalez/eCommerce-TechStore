# BKL-068 — GoF Observer: auditoría de pedidos

## Propósito

Demostrar un **Observer GoF real** y desacoplado en el Proyecto #3. El checkout de BKL-033 publica un evento de dominio; un observador concreto registra una evidencia SQL **solo después de confirmar el pedido**. No se envían correos ni se modifica el pedido desde el listener.

## Roles del patrón

| Elemento | Clase |
|---|---|
| Sujeto / notificador | SpringOrderEventPublisher detrás del puerto OrderEventPublisher |
| Evento | OrderCreatedEvent(orderId, userId, totalCrc) |
| Observador concreto | OrderAuditListener |
| Abstracción de persistencia | OrderEventAuditRepository |
| Persistencia de auditoría | JdbcOrderEventAuditRepository y Flyway V8 |

La **Facade** de BKL-033 solo conoce el puerto OrderEventPublisher; el observador puede reemplazarse o ampliarse sin modificar el caso de uso de checkout.

### Diagrama de clases (GoF Observer + Facade)

~~~mermaid
classDiagram
    class CheckoutFacade {
      +confirm(userId, shippingAddress) Order
    }
    class OrderEventPublisher {
      <<interface>>
      +orderCreated(event)
    }
    class SpringOrderEventPublisher {
      +orderCreated(event)
    }
    class OrderCreatedEvent {
      +orderId
      +userId
      +totalCrc
    }
    class OrderAuditListener {
      +onOrderCreated(event)
    }
    class OrderEventAuditRepository {
      <<interface>>
      +recordCreated(event)
    }
    class JdbcOrderEventAuditRepository {
      +recordCreated(event)
    }

    CheckoutFacade --> OrderEventPublisher : notifica
    OrderEventPublisher <|.. SpringOrderEventPublisher
    SpringOrderEventPublisher ..> OrderCreatedEvent : publica
    OrderAuditListener ..> OrderCreatedEvent : observa AFTER_COMMIT
    OrderAuditListener --> OrderEventAuditRepository : registra evidencia
    OrderEventAuditRepository <|.. JdbcOrderEventAuditRepository
~~~

### Secuencia del evento

~~~mermaid
sequenceDiagram
    participant Cliente
    participant Facade as CheckoutFacade
    participant DB as PostgreSQL
    participant Publisher as OrderEventPublisher
    participant Listener as OrderAuditListener
    participant Audit as AuditRepository (REQUIRES_NEW)
    Cliente->>Facade: confirmar compra
    Facade->>DB: pedido + detalle + reserva + vaciar carrito
    Facade->>Publisher: orderCreated(OrderCreatedEvent)
    Publisher-->>Listener: registra callback AFTER_COMMIT
    alt transacción confirma
      DB-->>Listener: AFTER_COMMIT
      Listener->>Audit: recordCreated(event)
      Audit->>DB: INSERT order_event_audit
      Audit-->>Listener: commit independiente
    else rollback
      DB-->>Listener: no entrega el callback
    end
    Facade-->>Cliente: pedido confirmado o error de checkout
~~~

## Persistencia y comportamiento

Flyway **V8** crea order_event_audit con FK a orders, event_type = ORDER_CREATED, total en CRC y observed_at. La combinación **(order_id, event_type) es única**: reemitir el evento no duplica filas (ON CONFLICT DO NOTHING). La tabla no incluye nombres, direcciones, correos ni números telefónicos.

El listener usa **@TransactionalEventListener(phase = AFTER_COMMIT)**. Como la transacción de checkout ya finalizó, JdbcOrderEventAuditRepository.recordCreated comienza una transacción independiente con **@Transactional(propagation = REQUIRES_NEW)**.

**Límite explícito:** este Observer es síncrono y de entrega **best effort**. Si el proceso falla después del commit pero antes de registrar auditoría, puede perderse el evento; el listener registra el error en logs sin cambiar el resultado de un pedido ya confirmado. **No hay transactional outbox, cola ni reintentos persistentes**. Para producción, incorporar un outbox escrito en la transacción del pedido y un publicador fiable. No confundir auditoría de creación con historial de cambios de estado (BKL-045).

## Evidencia y demostración

Con la API y PostgreSQL del proyecto, crear un pedido CLIENT mediante POST /api/orders. Después ejecutar:

~~~sql
SELECT order_id, event_type, total_crc, observed_at
FROM order_event_audit
ORDER BY observed_at DESC
LIMIT 20;
~~~

Para el pedido recién confirmado debe existir **una fila ORDER_CREATED**; un carrito rechazado o un checkout con rollback no produce ninguna. El mecanismo no expone un endpoint público de auditoría.

Pruebas automatizadas:

- OrderAuditListenerTest: delegación desacoplada y tratamiento de fallo de auditoría post-commit.
- OrderObserverIT: commit real produce evidencia; rollback mantiene carrito/stock y no audita; evento duplicado no crea dos registros.
- CheckoutConcurrencyIT: limpieza actualizada para las nuevas FK de auditoría.

Ejecución local con base aislada techstore_test:

~~~bash
mvn -f apps/api/pom.xml clean test
mvn -f apps/api/pom.xml clean verify
~~~

La CI utiliza PostgreSQL efímero, Flyway V8 y ejecuta ambos tipos de pruebas. No ejecutar pruebas de escritura contra una base de datos con información real.
