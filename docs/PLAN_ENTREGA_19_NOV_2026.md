# Plan vigente — Prototipo eCommerce TechStore
**Fecha de corte:** 14 de noviembre de 2026. **Entrega:** jueves **19 de noviembre de 2026** (Costa Rica).  
**Equipo:** Geovanni González Aguilar y Neyshell Blear Obregón.  
**Alcance confirmado:** un **prototipo ejecutable que demuestre las funcionalidades principales**, NO una plataforma comercial lista para producción.

> Este es el plan operativo vigente. Sustituye al plan histórico de 12 semanas y al cronograma acelerado anterior. Los campos S01–S12 de los Issues corresponden a la planificación original; no son fechas de entrega.

## Recorrido obligatorio de la demostración
1. Un visitante consulta el **catálogo público** de accesorios tecnológicos, con nombre, imagen, precio en colones y detalle.
2. Un **cliente se registra o inicia sesión** y no puede acceder a áreas administrativas.
3. Agrega artículos al **carrito**, cambia cantidades y ve subtotales.
4. Indica una **dirección de entrega**, revisa los importes calculados en el backend y confirma.
5. El sistema almacena un **Pedido y DetallePedido en PostgreSQL**, devuelve un identificador y muestra su estado.
6. Se presenta **pago simulado — solo demostración** sin capturar datos de tarjeta ni realizar cobros.
7. Se muestra una vista mínima de **administración** para consultar productos y, si hay tiempo, editarlos.
8. Se demuestra la ejecución desde README y evidencia básica de pruebas.

**Criterio de aceptación general:** crear un pedido persistente desde la interfaz sin modificar datos manualmente en la base. La simulación financiera debe identificarse de forma visible.

## Alcance mínimo / adicional / excluido
| Clasificación | Funcionalidades |
|---|---|
| **Obligatorio** | Aplicación local integrada (Next.js, Spring Boot, PostgreSQL), catálogo, autenticación básica, carrito, formulario de entrega, total y pedido persistido, confirmación y README |
| **Prioritario si queda tiempo** | CRUD básico admin, historial de pedidos, búsqueda y filtros |
| **Fuera de esta entrega** | Cobros reales, webhooks, reservas con caducidad, integración real de transportistas, reseñas, notificaciones, carga y alta disponibilidad |

**Seguridad básica:** contraseñas con hash, validación en servidor, autorización de rutas privadas, ninguna credencial versionada.  
**Inventario:** cantidades positivas y rechazo de solicitud si supera las existencias; la concurrencia avanzada queda documentada como limitación.  
**Pagos:** simulación sin dinero real; no afirmar que el prototipo ejecuta cobros ni ventas reales.

## Calendario operativo desde el 14 de noviembre
| Fecha | Geovanni — backend / integración | Neyshell — frontend / presentación | Criterio de cierre |
|---|---|---|---|
| **Sáb 14** | Verificar estado real del repositorio, arrancar Spring/PostgreSQL, modelo y API de productos | Arrancar Next.js, catálogo y ficha con datos de API | Navegador consulta productos de backend |
| **Dom 15** | Registro, autenticación, controles de roles y carrito API | Login, registro y carrito funcional | Cliente autenticado agrega/modifica artículos |
| **Lun 16** | Crear pedidos, detalles, dirección, validaciones y totales backend | Checkout y pantalla de confirmación/pago simulado | Se guarda pedido y se obtiene su identificador |
| **Mar 17** | Corregir errores del flujo, preparar administración si alcanza | Admin mínima, estados de pedidos y pruebas manuales | Demo completa repetible sin intervención técnica |
| **Mié 18** | Congelar funcionalidades, smoke tests, README y versión etiquetada | Capturas, guion de demo, prueba desde instalación limpia | Paquete entregable listo |
| **Jue 19** | Verificación de entrega, demo técnica | Revisión de evidencias y presentación | **Entrega** |

Las responsabilidades son sugeridas y deben ajustarse a disponibilidad real. Al quedar cinco días, trabajar en paralelo sobre contratos API acordados. Evitar desarrollar funciones que no se puedan integrar antes del 18.

## Priorización de GitHub Issues
Las etiquetas `delivery:must` identifican trabajo del flujo principal; `delivery:stretch` indica extensiones. Los Issues existentes conservan títulos y estimaciones de la versión completa. Para esta entrega se permite realizar **versiones reducidas y documentadas** de esas tareas sin afirmar que cumplen su alcance de producción.

Especialmente:
- **BKL-031**: dirección simple de entrega (sin integración geográfica).
- **BKL-032**: subtotal + costo de envío fijo configurable y total en servidor; no asumir cálculo fiscal definitivo.
- **BKL-033**: crear `Pedido` y `DetallePedido` y validar stock, **sin** prometer reservas temporales robustas.
- **BKL-023/024**: administración sencilla opcional para la demo; posponer si el checkout falla.
- **BKL-042/043**: historial opcional, posterior al guardado de pedidos.

## Puertas de decisión
- **15 nov. por la noche:** si faltan frontend/backend integrados, priorizar catálogo y un único flujo autenticado.
- **16 nov. por la noche:** si no se crea el pedido, detener funciones adicionales y corregir el checkout.
- **17 nov. por la noche:** no comenzar funciones nuevas, solo correcciones y documentación.
- **18 nov.:** congelar versión, ejecutar demostración integral y preparar entrega.

## Definition of Done del prototipo
- Se puede ejecutar siguiendo un README.
- Los flujos centrales funcionan con datos precargados.
- Las validaciones básicas se ejecutan en el backend.
- Se prueba una compra simulada y se confirma un pedido persistido.
- No se almacenan tarjetas ni secretos.
- Se conocen y documentan las limitaciones de la demostración.

## Coordinación
Actualización diaria breve; una tarea principal en curso por persona. Integrar y probar cambios diariamente. La fecha límite del 19 de noviembre no se desplaza por funcionalidades adicionales.

[Issues](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues) · [GitHub Project #7](https://github.com/users/Geovanni-Gonzalez/projects/7/views/1) · [Plan histórico](./PLAN_DE_DESARROLLO.md).
