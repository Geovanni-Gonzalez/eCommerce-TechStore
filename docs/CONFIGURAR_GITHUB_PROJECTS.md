# Configuración sugerida de GitHub Projects

El conector utilizado creó los **GitHub Issues**, pero **no** creó automáticamente un tablero GitHub Projects.

1. Abre el repositorio `Geovanni-Gonzalez/eCommerce-TechStore` en GitHub.
2. Abre **Projects** y crea un proyecto nuevo, por ejemplo `TechStore — Desarrollo`.
3. Usa una vista **Board** con estado: `Backlog`, `Ready`, `In Progress`, `In Review`, `Testing`, `Done`.
4. Añade los Issues del repositorio mediante **Add item from repository** o la opción equivalente.
5. Crea campos: `Iteration` (S01–S12), `Estimate (h)`, `Priority` y `Epic` si se necesitan como filtros adicionales. La información ya está escrita en cada Issue.
6. Filtra por etiquetas `area:EP01`–`area:EP12` y `priority:P0` / `priority:P1`.
7. Confirma el usuario de GitHub de Neyshell antes de asignar automáticamente tareas.
8. En Settings del repositorio, configura revisiones de PR y checks obligatorios para `main`, si están disponibles.
9. Refina el alcance real y las horas semanales antes de fijar fechas de iteración.

**La referencia principal es BKL-001 a BKL-064, no el número de Issue.** Una incidencia duplicada de BKL-021 (#22) fue cerrada y la original (#21) permanece abierta.

## Mínimos de seguimiento
- Estado de las tareas del sprint.
- Tareas bloqueadas.
- Pull requests por revisar.
- Errores críticos abiertos.
- Demostración semanal.
