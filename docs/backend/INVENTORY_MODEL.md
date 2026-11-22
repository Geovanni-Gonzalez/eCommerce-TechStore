# BKL-025 — Entidades, balance de inventario y movimientos auditados

## Estado previo y decisión de compatibilidad

La API ya reserva unidades al confirmar un pedido:
`JdbcCheckoutStockRepository.reserveForOrder` decrementa
`products.stock` y crea una fila `stock_reservations` con estado
`RESERVED` en la **misma transacción**. Cancelación/expiración cambian
`RESERVED → RELEASED` y devuelven unidades; conciliación de pago cambia
`RESERVED → CONSUMED` sin devolverlas.

Por ello, `products.stock` representa el **stock disponible**, no el
físico. Duplicar cantidades en una segunda tabla editable introduciría
riesgos de desincronización con pedidos existentes.

## Modelo implementado en Flyway V17

`inventory_balances` es una **VIEW**, no una tabla editable. Para cada
producto:

- **Disponible** = `products.stock` (constraint V1 `stock >= 0`).
- **Reservado** = suma de `stock_reservations.quantity` con
  `status='RESERVED'` (V7 `quantity BETWEEN 1 AND 99`).
- **Físico** = disponible + reservado.

Las lecturas comparten la instantánea transaccional de PostgreSQL. Los
balances se recalculan desde los datos actuales; no hay contadores
duplicados. Al consumir una reserva disminuye el físico; al liberarla
solo aumenta el disponible.

`inventory_movements` es una tabla de auditoría **append-only** que
conserva `product_id`, `order_id` opcional, tipo, cantidad y deltas
`available_delta`, `reserved_delta`, `physical_delta`, fecha UTC.
No guarda datos personales. Un trigger impide UPDATE y DELETE.

| Evento | Δ disponible | Δ reservado | Δ físico |
| --- | ---: | ---: | ---: |
| OPENING | saldo disponible | saldo reservado | suma de ambos |
| RESERVED | −q | +q | 0 |
| RELEASED | +q | −q | 0 |
| CONSUMED | 0 | −q | −q |
| ADJUSTMENT_IN | +q | 0 | +q |
| ADJUSTMENT_OUT | −q | 0 | −q |

Un trigger adicional impide borrar reservas, modificar su identidad o cantidad,
insertarlas directamente en estado terminal y regresar de RELEASED/CONSUMED
a RESERVED. Solo se permiten transiciones RESERVED → RELEASED/CONSUMED.
Esto evita modificar saldos reservados sin pasar por un evento auditado.

CHECK SQL garantiza `cantidad > 0`, igualdad
`Δ físico = Δ disponible + Δ reservado` y signos/deltas por tipo.
Para eventos de pedidos se exige `order_id`; para apertura y ajustes
se prohíbe.

Al migrar, V17 registra un único **OPENING** para cada producto con
existencias/reservas actuales. No reconstruye eventos individuales
anteriores a V17 porque esa información no existe. Los triggers
registran automáticamente aperturas futuras con stock positivo y
transiciones de `stock_reservations` en la misma transacción que
el pedido, incluyendo los caminos de BKL-027/BKL-040. Un error de
auditoría revierte la transacción.

## Entidades Java y acceso

`InventoryBalance`, `InventoryMovementKind` y
`InventoryMovement` validan las invariantes de lectura.

El puerto `InventoryReadRepository` y el adaptador
`JdbcInventoryReadRepository` permiten consultar balances y movimientos
de manera paginada y sin exponer endpoints. El panel futuro BKL-046
deberá proteger estos datos con ROLE_ADMIN.

## Límites y siguientes tareas

- **BKL-025 no añade un endpoint para editar stock.** BKL-026
  implementará ajustes transaccionales ADMIN que actualicen
  `products.stock` y registren `ADJUSTMENT_IN/OUT` en una misma
  transacción, con controles de saldo y sin afectar reservas.
- Las mutaciones directas ajenas al servicio de inventario/checkout
  no constituyen un flujo de negocio soportado. Cambiar
  `products.stock` por SQL manual después de V17 puede eludir
  auditoría; BKL-026 debe restringir ese acceso en producción.
- Los eventos históricos anteriores a V17 solo aparecen como
  saldo de apertura. El ID histórico no tiene FK a productos para
  preservar auditoría ante eventual eliminación física.
- **Pruebas de integración y aceptación pendientes para la fase final**,
  conforme a la indicación del equipo; no se deshabilita CI existente.

## Criterios para QA final

- Migración sobre BD con reservas `RESERVED/RELEASED/CONSUMED` y
  productos anteriores sin borrado de historial.
- Cantidad cero/negativa, deltas inconsistentes y duplicación de
  eventos deben ser rechazados por SQL.
- Verificar invariantes de balance al crear, cancelar, expirar y
  confirmar pedidos.
- Impedir editar o eliminar filas de auditoría.
- Mantener concurrencia en reservas con PostgreSQL real.
