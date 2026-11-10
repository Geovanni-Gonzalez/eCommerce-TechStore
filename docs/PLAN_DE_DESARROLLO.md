# Plan de desarrollo — eCommerce TechStore

**Estado:** propuesta inicial pendiente de refinamiento con el equipo.  
**Equipo:** Geovanni González Aguilar y Neyshell Blear Obregón.  
**Enfoque:** Scrumban ligero, iteraciones semanales, arquitectura monolítica modular.
**Tecnologías propuestas:** Next.js/TypeScript, Spring Boot/Java, PostgreSQL, Docker y GitHub Actions.
**Duración estimada:** 12 semanas. **Esfuerzo:** 340 horas conjuntas (estimación, no compromiso cerrado).

## Alcance del MVP
- Catálogo público de accesorios tecnológicos, búsqueda y filtros.
- Registro, autenticación y roles CLIENTE/ADMIN.
- Administración de catálogo, inventario y pedidos.
- Carrito persistente, checkout, direcciones en Costa Rica y totales calculados en servidor.
- Creación de pedidos y reservas de inventario para impedir sobreventa.
- Integración de pagos mediante pasarela externa en **sandbox**, con verificación de webhooks e idempotencia.
- Historial y estados de pedidos, controles de seguridad y pruebas de extremo a extremo.

## Funciones posteriores al MVP
- Seguimiento de envíos integrado o alternativa administrativa documentada.
- Reseñas verificadas y notificaciones desacopladas.
- Mejoras de rendimiento, accesibilidad, monitoreo y puesta en producción según los resultados de las pruebas.

## Hoja de ruta
| Iteración | Objetivo |
|---|---|
| S01 | Alcance, trabajo del equipo, wireframes, proveedores, modelo de datos |
| S02 | Monorepositorio, Next.js, Spring Boot, PostgreSQL, CI y OpenAPI |
| S03 | Autenticación, roles y catálogo básico |
| S04 | Búsqueda, filtros y catálogo administrativo |
| S05 | Inventario, reservas y carrito |
| S06 | Checkout, direcciones, totales y pedidos |
| S07 | Pasarela en sandbox, intentos de pago, webhooks |
| S08 | Conciliación, historial y pruebas de compra de extremo a extremo |
| S09 | Panel de pedidos, envíos y auditoría de permisos |
| S10 | Reseñas, notificaciones y accesibilidad |
| S11 | Concurrencia, seguridad, carga, regresión y corrección de defectos |
| S12 | Staging, respaldos, monitoreo, documentación y demostración final |

## Organización del trabajo
- **Geovanni (propuesto):** backend, base de datos, transacciones, pasarela de pago, CI/CD y despliegue.
- **Neyshell (propuesto):** interfaz, experiencia de usuario, administración en frontend, pruebas E2E y documentación.
- **Compartido:** decisiones de arquitectura, revisión cruzada, riesgos, pruebas y aceptación semanal.
- **Importante:** las asignaciones son propuestas; no se asignan Issues a un usuario de GitHub sin confirmar su identidad y disponibilidad.

## Flujo de gestión
Backlog → Ready → In Progress → In Review → Testing → Done. Usar `blocked` si una tarea no puede avanzar; limitar a una tarea principal en progreso por persona.

Cada Issue tiene identificador BKL, épica EP, prioridad P0/P1, sprint S01–S12, horas, dependencias y criterios de aceptación. Los IDs BKL son estables, independientemente del número de Issue.

## Definition of Ready
- Resultado esperado y criterios de aceptación definidos.
- Dependencias y riesgos conocidos.
- Responsable y estimación revisados.
- Tarea suficientemente pequeña para una iteración.

## Definition of Done
- Criterios de aceptación cumplidos.
- Cambios revisados por el otro integrante mediante pull request.
- Pruebas pertinentes superadas y CI en verde.
- Sin secretos ni credenciales expuestas.
- Contratos y migraciones documentados cuando proceda.
- Funcionalidad demostrable y Issue actualizado.

## Cadencia
- Lunes: planificación semanal (30–45 min).
- Mitad de semana: sincronización de integración (20 min).
- Viernes: demo de lo terminado (30 min) y retrospectiva (15 min).
- Aviso de bloqueos tan pronto se identifiquen.

## Hitos
1. **S02:** sistema base reproducible y CI operativa.
2. **S05:** catálogo, administración e inventario funcionales.
3. **S08:** compra completa verificable con pagos sandbox.
4. **S12:** versión candidata estable, documentada y demostrable.

## Riesgos prioritarios
1. Disponibilidad de proveedor de pago para Costa Rica y pruebas con CRC.
2. Complejidad del proveedor logístico: validar temprano y mantener alternativa manual.
3. Sobreventa: usar reservas y pruebas de concurrencia.
4. Estados financieros inconsistentes: webhooks verificados, idempotencia y conciliación.
5. Disponibilidad académica: adaptar el alcance según horas reales del equipo.

## Registro de decisiones
Mantener decisiones importantes en `docs/adr/` (contexto, opciones, elección y consecuencias). El PDF de arquitectura es la línea base; toda desviación significativa deberá registrarse.

## Enlaces
- [Backlog en GitHub Issues](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues)
- [Configuración manual de GitHub Projects](./CONFIGURAR_GITHUB_PROJECTS.md)
