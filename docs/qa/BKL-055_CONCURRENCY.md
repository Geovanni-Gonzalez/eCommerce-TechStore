# BKL-055 — Concurrencia de checkout y caducidad de reservas

## Casos automatizados (PostgreSQL real, base aislada `techstore_test`)

`CheckoutConcurrencyIT` ahora cubre:

1. **Competencia por la última unidad:** dos clientes disparan
   `CheckoutFacade.confirm` en paralelo. Solo se crea un pedido, no
   hay sobreventa, el carrito del perdedor conserva su artículo y existe
   una única reserva.
2. **Doble worker de caducidad:** dos invocaciones simultáneas
   `OrderReservationService.expireOne` compiten por el mismo pedido vencido.
   El bloqueo `orders FOR UPDATE` permite que solo una cambie estado;
   comprueba única transición `EXPIRED`, reserva `RELEASED` y liberación
   de exactamente una unidad.
3. **Reutilización de stock liberado:** segundo cliente confirma un pedido
   tras la expiración; el stock vuelve a cero, sin doble liberación.

## Pasos reproducibles

- Usar PostgreSQL de pruebas `techstore_test`, **nunca producción**.
- Ejecutar `mvn -B -ntp -f apps/api/pom.xml clean verify`.
- Comprobar que las pruebas `CheckoutConcurrencyIT` y demás IT pasan.
- En cargas repetidas, observar posibles interbloqueos y fijar orden de locks
  `orders → stock_reservations → products` cuando corresponda.

## Riesgos no cubiertos todavía

No se han ejecutado cargas prolongadas, repetición estocástica elevada,
estrés con pagos aprobados tardíos, múltiples nodos ni medición p95 bajo
concurrencia real. No marcar el issue como Done hasta registrar CI,
revisión cruzada y escenarios de pago tardío de BKL-040.
