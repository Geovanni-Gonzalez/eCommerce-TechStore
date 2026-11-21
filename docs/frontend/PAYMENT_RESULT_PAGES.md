# BKL-039 — Páginas de resultado de simulación verificadas por backend

## Objetivo y límites

TechStore es un prototipo académico. **No recibe dinero real**. Una URL de retorno como `/pagos/resultado/aprobado`, parámetros `status=approved` o una respuesta de la sesión de sandbox **no prueban que exista un pago conciliado**. Las tres vistas son **aprobado, rechazado y pendiente**, pero el estado presentado solo se determina a partir de datos autenticados de la API y un **usuario propietario**.

## Rutas

- `/pagos/resultado?orderId=27&attemptId=98`: entrada neutra desde la página de sesión sandbox tras consumir el token.
- `/pagos/resultado/aprobado?orderId=27&attemptId=98`
- `/pagos/resultado/rechazado?orderId=27&attemptId=98`
- `/pagos/resultado/pendiente?orderId=27&attemptId=98`

Los dos ID son **solo localizadores** (enteros positivos); no determinan resultado, importe, propietario ni autorización. El segmento `/aprobado`, aunque se escriba directamente, nunca muestra una aprobación sin consultar el backend. Una vez verificado el estado, la página **mantiene el resultado visible sin navegar**, para no desmontar el componente React ni perder el token transitorio; esto fue validado con Chromium en BKL-044. Las rutas alternativas siguen disponibles para acceso directo, pero su segmento nunca determina el resultado. Todas las páginas definen `robots: noindex` y `referrer: no-referrer`.

## Autenticación transitoria

BKL-017 todavía no ofrece inicio de sesión en el frontend. Para que BKL-039 funcione sin almacenar credenciales de forma permanente, la pantalla pide **correo y contraseña del cliente propietario** y llama a `POST /api/auth/login` por HTTPS en despliegues reales (HTTP local únicamente para demos). El token Bearer opaco revocable se mantiene en un `useRef` de React **solo durante esa consulta**, nunca se añade a URL, cookie, localStorage ni sessionStorage. La contraseña se limpia al recibir la respuesta o un error. “Cerrar consulta segura” borra el token local e invoca `POST /api/auth/logout` para revocarlo cuando el servicio está accesible.

El cliente realiza dos GET autenticados con `cache: no-store`, `credentials: omit` y `Referrer-Policy: no-referrer`:

1. `GET /api/orders/{orderId}` — BKL-042 comprueba `WHERE id=? AND user_id=?`, de modo que pedido ajeno e inexistente devuelven el mismo 404.
2. `GET /api/orders/{orderId}/payment-attempts/{attemptId}` — BKL-036 exige el mismo propietario de pedido e intento, con estado persistido del proveedor sandbox.

Si uno falla, se muestra **No se pudo verificar**, nunca un resultado aprobatorio. No se muestran direcciones de envío, tokens, contraseñas ni referencias del proveedor en la página. El botón “Comprobar de nuevo” vuelve a leer ambas fuentes, útil ante conciliación asíncrona BKL-038/040/041.

## Tabla de clasificación

| Pedido | Intento | Resultado visible |
| --- | --- | --- |
| `CONFIRMED` y `paymentMode=SIMULATED` | `SIMULATED_APPROVED` | **Simulación aprobada**; claramente no es recibo bancario |
| `PENDING` o `CANCELLED` | `SIMULATED_DECLINED` | **Simulación rechazada** |
| `PENDING` | `SIMULATED_APPROVED` | **Pendiente de conciliación**; nunca “pagado” |
| Cualquier otro estado, `STARTED`, `UNKNOWN`, `UNAVAILABLE`, incoherencia | Cualquiera | **Pendiente** o error de consulta |

Antes de clasificar se validan los IDs de ambas respuestas (`order.id`, `attempt.orderId`, `attempt.id`), la modalidad `SIMULATED`, el esquema esperado y la igualdad del total de pedido e intento en céntimos `CRC`. Un estado desconocido o datos mal formados devuelven **pending**, nunca `approved`.

La ruta no ejecuta `POST payment-attempts` ni reintenta la autorización; solo consulta datos. No modifica stock, historial, reservas ni pagos. El backend puede confirmar pedidos simulados únicamente bajo BKL-040 con opt-in y webhook HMAC; por defecto, el aprobado del sandbox sin conciliación permanece pendiente.

## Pruebas y CI

`apps/web/src/lib/payment-result.test.mjs` ejercita `classifyPaymentResult` y `parseResultId`: casos aprobados, rechazados, inciertos, pedidos cancelados, respuestas contradictorias, montos/identificadores inconsistentes, estados falsificados e IDs manipulados. La CI Node.js 22 ejecuta estas pruebas sin añadir dependencias.

El *smoke test* HTTP abre las cuatro rutas y verifica que el servidor de producción Next.js **no afirma que un pedido fue confirmado por el backend antes de autenticar**. Las rutas se incluyen además en lint, typecheck y build de Next.js. Para probar la UI manualmente, usar el sandbox local habilitado sin dinero real, completar `/pagos/sandbox` y acceder al enlace de resultado; un evento HMAC verificado con BKL-040 opt-in puede cambiar `PENDING` a `CONFIRMED` en modo simulación.

**Limitación explícita:** BKL-017 debe unificar el formulario de login y la gestión de la sesión del frontend. Esta consulta puntual evita bloquear BKL-039, pero no sustituye el flujo general de autenticación. En ningún caso debe utilizarse una pantalla simulada como prueba de fondos reales.

## Origen seguro de credenciales

La función `isSafeApiOrigin` bloquea autenticación y consultas Bearer cuando `NEXT_PUBLIC_API_URL` usa **HTTP remoto**, credenciales incrustadas en la URL, querystring, fragmentos o rutas inesperadas. Solo admite **HTTPS** o **HTTP en loopback local** (`localhost`, `127.0.0.1`, `::1`) para la demostración. Hay pruebas de URLs peligrosas en la suite Node 22.
