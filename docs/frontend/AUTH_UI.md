# BKL-017 — Registro e inicio de sesión (Next.js)

## Funcionalidad

- `/registro` envía `POST /api/auth/register` a Spring Boot y solo permite alta pública de CLIENT. La creación de cuenta devuelve 201, **no** autentica automáticamente; la UI muestra enlace a `/login`.
- `/login` utiliza `POST /api/auth/login`. Se valida el Bearer opaco y el objeto `user` de la API; nunca se trata como JWT.
- Redirección según rol devuelto por la API: CLIENT → `/cuenta`, ADMIN → `/admin`.
- La pantalla `/admin` exige rol ADMIN en el estado de sesión y comprueba además `GET /api/admin/check`; el panel CRUD no existe todavía.
- Logout llama a `POST /api/auth/logout` y borra inmediatamente la sesión en memoria; si la API falla el token deja de estar en memoria igualmente.
- Los formularios muestran errores de credenciales, duplicados y servidor como mensajes accesibles (`role=alert`); tienen etiquetas, autocomplete y foco visible.
- Se verifica `NEXT_PUBLIC_API_URL`: HTTPS o HTTP loopback, sin credenciales en URL; las solicitudes no envían cookies.

## Decisión de seguridad y límites

El Bearer opaco se conserva **solo en un ref React del proveedor compartido en el layout**; el perfil público está en estado React. No se utiliza `localStorage`, `sessionStorage`, URL ni cookies. Se pierde la sesión al refrescar la página; el backend mantiene la token anterior hasta vencimiento o revocación. Es una decisión para el prototipo; producción requiere BFF/cookies seguras u otra estrategia auditada, además de protección contra abuso.

`/pedidos` y `/pagos/resultado` mantienen temporalmente su propia autenticación local (BKL-043/039) para no alterar sus pruebas E2E y reglas de propiedad. **Visitar el historial puede volver a solicitar credenciales**; la sesión global se conectará en la etapa de carrito y checkout. Nunca se afirma que una pantalla administrativa o la compra estén terminadas.

## Validación manual

1. Levanta PostgreSQL (Docker) y Spring Boot con `SPRING_DATASOURCE_PASSWORD`; inicia Next.js con `npm run dev:web`.
2. Visita `http://localhost:3000/registro`, crea un cliente de prueba, verifica confirmación.
3. Visita `http://localhost:3000/login` e inicia sesión. Debe redirigir a `/cuenta`, mostrar rol CLIENT y ofrecer cierre de sesión.
4. Comprueba que `/admin` no concede permisos a CLIENT. Al salir, `/cuenta` ya no muestra información personal.
5. Para ADMIN, crear cuenta únicamente por mecanismo seguro del backend en un entorno controlado; tras ingresar, `/admin` exige comprobación real de la API.

## Pruebas automáticas

```powershell
node --experimental-strip-types --test apps/web/src/lib/auth.test.mjs
npm run lint:web
npm run typecheck:web
npm run build:web
```

En GitHub Actions, `scripts/ci/e2e-auth.mjs` verifica mediante Chromium → Next.js → API → PostgreSQL efímero el registro, redirección del cliente, rechazo del acceso ADMIN y cierre de sesión, junto a las regresiones BKL-044.
