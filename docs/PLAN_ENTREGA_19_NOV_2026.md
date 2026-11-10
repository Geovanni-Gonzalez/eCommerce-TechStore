# Plan de entrega acelerada — eCommerce TechStore
**Fecha límite fija:** jueves **19 de noviembre de 2026** (Costa Rica).
**Creación:** 9 de noviembre de 2026. **Equipo:** Geovanni González Aguilar y Neyshell Blear Obregón.
**Estado:** propuesta urgente, ajustar alcance según dedicación y estado real del código.

> Este plan **reemplaza** el calendario de 12 semanas. Las 64 incidencias BKL originales siguen siendo el backlog de producto, pero **NO** son compromisos para el 19 de noviembre. Las etiquetas `delivery:must` y `delivery:stretch` distinguen el trabajo de entrega; las indicaciones S01-S12 dentro de las incidencias son históricas.

## Restricción de capacidad
El plan original tenía 340 h estimadas. Para ejecutarlo en diez días se necesitarían ~17 h por persona por día: **no es realista**.
Presupuestar aproximadamente **4 a 6 h por persona y día de desarrollo** durante nueve días (10–18 nov.) supone 72–108 horas conjuntas. Ajustar a la disponibilidad real.
**No comenzar nuevas funciones durante los últimos dos días si el flujo principal todavía falla.**

## Objetivo verificable de entrega
Poder demostrar en una computadora una aplicación local integrada con:
1. Catálogo público con productos precargados y detalle.
2. Registro/autenticación mínima para cliente y cuenta admin de pruebas; rutas privadas protegidas.
3. Carrito con cantidades y subtotal.
4. Checkout que crea un **pedido de demostración** con datos de entrega y monto confirmado por el servidor.
5. Historial/estado básico del pedido **si alcanza el tiempo**.
6. Gestión básica de productos en administración **si alcanza el tiempo**.
7. Código y esquema SQL versionados, instrucciones de arranque y evidencia de pruebas básicas.

**Pagos:** la arquitectura contempla una pasarela externa, pero no se garantiza integración real en diez días. Solo utilizar sandbox si se confirma proveedor y credenciales desde el 10–11 nov. Si no, mostrar claramente `pago simulado (solo demostración)` y **nunca** afirmar que se hizo un cobro, verificar un pago real o habilitar ventas reales. Las pruebas no deben usar tarjetas ni dinero real.

**Inventario:** priorizar que cantidades negativas y pedidos sin stock sean rechazados. Bloqueos, reservas temporales y conciliación de pagos quedan pendientes si no hay tiempo; documentar riesgos y limitaciones. El checkout de demostración no equivale a un sistema listo para producción.

## Alcance que se difiere salvo que sobre tiempo
Integración de transportista, reseñas, notificaciones, favoritos, optimizaciones avanzadas, despliegue público, alta disponibilidad, auditoría extensa, pasarela productiva, sistema robusto de reintentos y reservas con caducidad.
No prometer WCAG completa, rendimiento objetivo ni cumplimiento normativo integral sin pruebas.

## Calendario por día
| Fecha | Geovanni (responsable sugerido) | Neyshell (responsable sugerida) | Puerta de salida |
|---|---|---|---|
| 10 nov (mar) | Configurar Spring Boot, PostgreSQL, migraciones y API base | Next.js, diseño mínimo y estructura de rutas | Ambos proyectos arrancan localmente |
| 11 nov (mié) | API de catálogo con productos de ejemplo | Catálogo y ficha con datos reales de API | Catálogo visible desde navegador |
| 12 nov (jue) | Registro, autenticación y rol admin | Formularios de login/registro y estados | Usuario puede entrar y cerrar sesión |
| 13 nov (vie) | API de productos administrable si hay capacidad | Panel admin simple si hay capacidad | Catálogo de punta a punta estable |
| 14 nov (sáb) | API de carrito, validación de cantidades | Carrito persistente y resumen | Carrito operativo |
| 15 nov (dom) | Pedido, detalles, monto calculado por backend | Checkout y captura de datos de entrega | Se crea pedido demostrable |
| 16 nov (lun) | Stock mínimo y pago sandbox solo si es viable; si no, flujo simulado explícito | Confirmación, error y estado del pedido | **Demo completa de compra** |
| 17 nov (mar) | Pruebas de API, autenticación y defectos críticos | Pruebas manuales/E2E, móvil y correcciones | Sin bloqueadores del flujo principal |
| 18 nov (mié) | Congelar funcionalidades, preparar release y respaldo local | README, capturas/video, guía de demo y revisión final | Versión entregable |
| 19 nov (jue) | **Entrega:** verificar repositorio y archivos finales | **Entrega:** validar presentación y evidencia | Presentado y entregado dentro del plazo |

## Puntos de control y reducción de alcance
- Si el **11 nov.** no funcionan Next, Spring y PostgreSQL integrados, suspender admin y pagos externos hasta resolver la base.
- Si el **13 nov.** no funciona catálogo + login, terminar esos flujos antes de iniciar carrito.
- Si el **16 nov.** no hay checkout funcional, eliminar extras y concentrarse en una demo coherente.
- **18 nov.** congelar funcionalidades: solo corrección de fallos, pruebas y preparación de entrega.

## Backlog para esta entrega
Los asuntos con `delivery:must` forman el núcleo de implementación **prioritario**, no una garantía de completar cada issue tal como estaba dimensionado en el plan de 12 semanas. Reducir el trabajo y los criterios de aceptación mediante refinamiento explícito cuando sea necesario.
Los asuntos `delivery:stretch` se intentan solo una vez que el núcleo pasa pruebas.
El resto de las 64 tareas es posentrega o pertenece a la visión del producto.

## Reglas de trabajo
- Máximo una tarea en progreso por integrante.
- Integración diaria en `main` mediante PR pequeños revisados por el compañero.
- Todos los secretos en variables de entorno; nunca en commits.
- Verificar cada día que una persona nueva pueda ejecutar el proyecto siguiendo README.
- Actualización breve a las 20:00: hecho, siguiente paso y bloqueos.
- Documentar expresamente qué funciones son prototipo, simulación o pendientes.

## Decisiones pendientes para el equipo (10 de noviembre)
1. Horas reales disponibles por día.
2. Qué exactamente debe entregarse (código ejecutable, vídeo, documentación, presentación).
3. Identidad de GitHub de Neyshell para asignar asuntos.
4. Si existe una pasarela de pago sandbox viable en Costa Rica.
5. Si se trabajará fin de semana: el cronograma supone que sí.

## Hitos
- **11 nov.:** catálogo conectado.
- **13 nov.:** identidad básica y catálogo listos.
- **16 nov.:** demostración integrada de compra sin cobro real salvo sandbox verificado.
- **18 nov.:** versión congelada y materiales listos.
- **19 nov.:** entrega.

## Relación con el plan anterior
[Plan original de 12 semanas — histórico](./PLAN_DE_DESARROLLO.md) · [Issues](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues) · [Project #7](https://github.com/users/Geovanni-Gonzalez/projects/7/views/1).
