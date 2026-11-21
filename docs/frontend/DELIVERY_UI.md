# BKL-031 — Formulario de entrega para Costa Rica

Issue: https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/32

## Alcance implementado
- Ruta /checkout/entrega para cuentas CLIENT autenticadas mediante BKL-017.
- Provincia seleccionable entre las siete provincias; cantón y distrito escritos
  manualmente, sin afirmar que exista validación oficial de su combinación.
- Señas/referencias, nombre de quien recibe y teléfono obligatorios.
- Validador TypeScript equivalente al dominio ShippingAddress.java:
  provincia, cantón y distrito 1–80 caracteres; señas 5–300;
  destinatario 2–120; teléfono 8–24; ningún carácter de control.
- Errores asociados con aria-invalid y aria-describedby; foco en el primer
  campo inválido, contraste y diseño responsive.
- Guardar y revisar muestra un resumen editable; el borrador está en estado
  React de AuthProvider y se elimina al cerrar sesión; no usa URL, cookies,
  localStorage ni sessionStorage.
- Enlace desde carrito cuando la API confirma checkoutReady=true.
- Helper makeShippingAddressBody prepara solo los seis campos del contrato
  POST /api/orders; esta ruta NO envía ese POST.

## Límites
- La API no tiene un endpoint independiente para guardar direcciones.
  La dirección se persistirá en el snapshot del pedido cuando el checkout
  BKL-034 ejecute POST /api/orders con la sesión CLIENT.
- La dirección se pierde tras recargar la página; esto conserva la política
  de token exclusivamente en memoria del prototipo BKL-017.
- No se calcula cotización ni se realiza pago, pedido o reserva de stock.
- No se verifica oficialmente que distrito/cantón pertenezcan a una provincia.

## Verificación manual
1. Iniciar Spring Boot, PostgreSQL y Next.js; crear/login como cliente.
2. Agregar producto en /productos, abrir /carrito y pulsar
   Preparar dirección de entrega.
3. Enviar vacío: deben aparecer seis errores accesibles.
4. Rellenar los seis campos con datos ficticios; guardar y revisar.
5. Editar distrito, volver al carrito y regresar sin recargar: el borrador
   debe conservarse.
6. Cerrar sesión en /cuenta, abrir /checkout/entrega y confirmar
   que no aparecen datos personales.

## Pruebas
- node --experimental-strip-types --test apps/web/src/lib/shipping-address.test.mjs
- npm run lint:web
- npm run typecheck:web
- npm run build:web
- GitHub CI: scripts/ci/e2e-delivery.mjs sobre Chromium, Spring Boot
  y PostgreSQL aislado en matriz APPROVED/DECLINED; BKL-044 continúa.
