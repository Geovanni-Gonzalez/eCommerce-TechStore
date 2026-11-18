# Plan vigente — Prototipo eCommerce TechStore

> **ACTUALIZACIÓN DE CALENDARIO:** el equipo comunicó que la entrega se trasladó al **sábado 21 de noviembre de 2026, 10:00 p. m. (Costa Rica)**. Este cambio sustituye el plazo de 19 noviembre en el cronograma previo; el nombre histórico de este archivo se conserva para no romper enlaces.

> **ATENCIÓN — Consigna oficial Proyecto #3:** La aplicación debe aplicar **Modelo–Vista–Controlador (MVC)** y **mínimo tres patrones GoF**; además debe demostrar **UX, UI y accesibilidad** en la exposición. El repositorio debe ser **PÚBLICO**. **Fecha límite: sábado 21 de noviembre de 2026 a las 10:00 p. m. (hora de Costa Rica)**. La configuración actual del repositorio es privada; su propietario debe revisar secretos y hacerlo público antes de entregar.

**Fecha de corte inicial:** 14 de noviembre de 2026. **Nueva fecha de entrega comunicada por el equipo:** 18 de noviembre de 2026. **Entrega:** sábado **21 de noviembre de 2026 a las 10:00 p. m.** (Costa Rica).  
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
Actualización diaria breve; una tarea principal en curso por persona. Integrar y probar cambios diariamente. La fecha límite del 21 de noviembre no se desplaza por funcionalidades adicionales.

[Issues](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues) · [GitHub Project #7](https://github.com/users/Geovanni-Gonzalez/projects/7/views/1) · [Plan histórico](./PLAN_DE_DESARROLLO.md).

## Requisitos obligatorios de la consigna — Proyecto #3
**Estos requisitos prevalecen sobre las características opcionales del MVP.** La documentación de arquitectura por sí sola no cumple la consigna: los patrones deberán existir en el código ejecutable y ser demostrables.

| Requisito | Aplicación concreta en TechStore | Issue |
|---|---|---|
| Modelo–Vista–Controlador | Modelo: entidades y dominio Spring Boot. Controlador: controladores REST Spring. Vista: interfaces Next.js; explicar que frontend y backend son separados y documentar el flujo | [BKL-065](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/66) |
| GoF Strategy | Dos políticas intercambiables de costo de envío (tarifa fija, envío gratuito por umbral) usadas por checkout | [BKL-066](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/67) |
| GoF Facade | `CheckoutFacade` coordina validaciones, cálculo y persistencia, sin lógica de negocio en el controlador | [BKL-067](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/68) |
| GoF Observer | Evento de pedido creado y observador que registra auditoría o notificación **simulada** | [BKL-068](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/69) |
| UX, UI, Accesibilidad | Navegación clara, diseño responsivo, etiquetas, navegación con teclado, foco visible y estados de error | [BKL-069](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/70) |
| Repositorio público | Inspeccionar secretos/datos, configurar visibilidad pública por el propietario y probar enlace sin iniciar sesión | [BKL-070](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/71) |
| Exposición | Demostración del sistema en funcionamiento, explicación y localización de los tres patrones en el código, pruebas UX/UI | [BKL-071](https://github.com/Geovanni-Gonzalez/eCommerce-TechStore/issues/72) |

### Ajuste de prioridades y actividades
- **14–15 noviembre:** base técnica, catálogo y preparación de clases/interfaces para los tres patrones; diseñar con MVC.
- **16 noviembre:** terminar carrito y pedido persistente; implementar Facade + Strategy dentro del checkout.
- **17 noviembre:** implementar Observer y mostrar los tres patrones ejecutándose; UX/UI/accesibilidad, integración y pruebas.
- **18 noviembre:** congelar el código, ensayar exposición, revisar secretos y publicar repositorio **tras autorización del propietario**; preparar README y plan de respaldo.
- **21 noviembre antes de las 22:00:** realizar entrega y exposición según instrucciones del docente.

**Si falta tiempo:** reducir funciones de negocio secundarias (administración, reseñas, logística externa), **no** omitir MVC, los tres patrones, el repositorio público o la exposición.
