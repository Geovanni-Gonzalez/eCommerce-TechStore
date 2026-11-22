# BKL-054 — Cobertura accesibilidad WCAG de flujos principales

## Controles automáticos añadidos

Se ejecuta `scripts/ci/e2e-accessibility.mjs` en Chromium de CI
en los escenarios APPROVED y DECLINED ya configurados. No se modifica
el resultado del pago ni se afirma validez WCAG completa.

| Control | Criterio orientativo WCAG 2.2 AA | Evidencia |
| --- | --- | --- |
| Enlace `Saltar al contenido` como primer Tab y destino `#contenido` | 2.4.1 Bypass Blocks | Verificación real de teclado en Chromium |
| Anillo de foco visible (>=2px en enlace inicial) | 2.4.7 Focus Visible | Estilos computados con teclado, 1280/390px |
| Inputs de catálogo, búsqueda y login identificables por etiqueta | 3.3.2 Labels or Instructions | Consultas Playwright por nombre accesible |
| Sin desbordamiento horizontal en páginas públicas clave a 390px | 1.4.10 Reflow (verificar excepciones) | Comparación `scrollWidth/clientWidth` en Chromium |
| Pantallas con encabezado h1 identificable | 1.3.1 Info and Relationships | Consulta de encabezados `level: 1` |

Páginas automatizadas: `/productos`, `/login`, `/registro` a
1280×800 y 390×844. El resto de pantallas se mantiene como pendiente
de evaluación manual. El check no mide contraste.

## Prueba manual obligatoria antes de Done

- [ ] 1.4.3 / 1.4.11: medir contraste de texto, borde y controles
  en Chrome DevTools o herramienta WCAG; guardar valores.
- [ ] 1.4.10: reflow con zoom 200% y 400%, sin perder contenido o acciones.
- [ ] 2.1.1/2.1.2: recorrer registro, login, catálogo, carrito, entrega,
  checkout, pedidos y reseñas exclusivamente con teclado; sin trampas.
- [ ] 2.4.7/2.4.11: foco visible y no oculto por cabeceras/superposiciones.
- [ ] 3.3.1/3.3.3: invalidar formularios y verificar mensajes asociados,
  solución posible y recuperación de entrada.
- [ ] 1.1.1: imágenes con alternativa textual adecuada.
- [ ] Lector de pantalla (NVDA/VoiceOver): orden lógico, estados anunciados
  mediante `role=alert` / `role=status`, grupo de estrellas de BKL-051.
- [ ] Dispositivos táctiles: tamaño de los controles y menú a 320–390px.

## Defectos y seguimiento

Registrar hallazgos con ruta, anchura, modo de interacción, criterio WCAG,
resultado esperado, resultado observado, captura y severidad.
**Pendiente:** ejecutar auditoría manual y anexar evidencia; un check
automático verde por sí solo NO cierra BKL-054.

## Comandos

Con API, PostgreSQL y frontend levantados:

`node scripts/ci/e2e-accessibility.mjs`

En CI se instala temporalmente `playwright@1.56.1` y Chromium, sin
agregar una dependencia de ejecución al frontend. Las capturas ante
fallo se guardan en `artifacts/e2e/failure-a11y.png`.
