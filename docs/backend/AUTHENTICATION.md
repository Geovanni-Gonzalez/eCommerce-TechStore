# BKL-016 — Autenticación, sesiones y autorización

## Estado implementado

El módulo usa **Spring Security**, PostgreSQL y un token **Bearer opaco**, no JWT. El usuario obtiene el token después de autenticar su correo y contraseña BCrypt y lo presenta en el encabezado `Authorization: Bearer <token>` para consultar endpoints privados. La sesión puede revocarse inmediatamente desde logout; para un prototipo académico es más fácil de demostrar y verificar que una lista de revocación de JWT.

### Endpoints

| Método | Ruta | Seguridad | Resultado |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | Pública; correo/contraseña | 200 AuthSession; 400 formato; 401 credenciales inválidas |
| POST | `/api/auth/logout` | Bearer válido | 204 y token revocado; 401 sin token |
| GET | `/api/auth/me` | Bearer válido | 200 User, sin hash; 401 sin token |
| GET | `/api/admin/check` | Bearer con `ADMIN` | 200; 401 sin sesión; 403 si CLIENTE |
| GET | `/api/products`, `/api/products/{id}` | Públicas | Mantienen catálogo de BKL-018 |
| POST | `/api/auth/register` | Pública | Mantiene registro de BKL-015 |
| `/api/orders/**` | Privadas | Exigen Bearer desde ahora; la lógica de pedidos sigue **planificada** |

`/api/admin/check` **no representa un panel de administración funcional**. Sirve para demostrar el control de acceso basado en rol. Los endpoints administrativos de negocio se añadirán por separado.

## Decisión arquitectónica: token opaco con revocación

Flyway `V4__create_auth_sessions.sql` crea `auth_sessions` sin modificar V1–V3: `user_id` como FK, `token_hash` único, `expires_at` y `revoked_at`. `AccessTokenCodec` genera 32 bytes mediante `SecureRandom` (256 bits de entropía), los codifica como Base64 URL-safe sin relleno y calcula SHA-256. **Solo se guarda el hash** en `auth_sessions`; el token completo aparece únicamente en la respuesta de login. La sesión dura **8 horas**, no incluye mecanismo de renovación y el usuario puede revocarla mediante logout. Cada nuevo login genera una sesión independiente.

El filtro `BearerTokenAuthenticationFilter` consulta por hash y acepta únicamente sesiones **no expiradas y no revocadas**, luego vuelve a comprobar que el usuario esté activo y utiliza el rol actual de `users`. De ese modo, desactivar una cuenta o cambiar sus permisos tiene efecto en la siguiente solicitud. `ApiSecurityConfig` restringe `/api/admin/**` a `ROLE_ADMIN` y protege las restantes rutas privadas; devuelve errores uniformes `application/problem+json` para 401/403.

El backend **no utiliza cookies de autenticación, HTTP Basic ni formularios de Spring Security**. El servidor no almacena sesión HTTP; se valida el Bearer por solicitud. CSRF se desactiva específicamente por usar Bearer en cabeceras y no autenticación basada en cookies. El CORS mantiene una lista explícita de orígenes y no admite credenciales con cookies.

### Contrato y roles

El rol interno PostgreSQL `CLIENTE` se presenta en el JSON público como `CLIENT`; `ADMIN` se presenta como `ADMIN`. `AuthSession` responde exactamente a `{accessToken, tokenType: "Bearer", user: {id, displayName, email, role}}`. No se expone `password_hash` en el JSON ni se registra el token en los logs. El esquema OpenAPI declara `bearerFormat: "Opaque"`: **no debe describirse como JWT**.

### Aplicación de SOLID y GRASP

```text
AuthenticationController → AuthenticationService
                                     ├→ UserAccountRepository → JDBC users
                                     ├→ PasswordHasher → BCrypt
                                     ├→ SessionTokenRepository → JDBC auth_sessions
                                     └→ AccessTokenCodec → SecureRandom / SHA-256

Petición privada → Spring Security → BearerTokenAuthenticationFilter
                                      ├→ SessionTokenRepository
                                      └→ UserAccountRepository
                                 → permiso (ROLE_CLIENT / ROLE_ADMIN)
```

**SRP:** controlador traduce HTTP, servicio gestiona casos de uso, codec genera/hashea tokens, repositorios persisten y el filtro autentica. **DIP/bajo acoplamiento:** servicio y filtro dependen de interfaces de repositorio. **GRASP Controller/Experto:** las políticas de acceso están en `ApiSecurityConfig`, no repetidas en los controladores comerciales.

## Pruebas y ejecución

La CI en GitHub Actions crea PostgreSQL 17 efímero con base `techstore_test`, aplica Flyway V1–V4 y ejecuta `mvn -f apps/api/pom.xml clean verify`. `AuthenticationServiceTest` verifica credenciales, normalización, aleatoriedad y revocación sin PostgreSQL. `AuthenticationIT` utiliza el filtro de Spring Security y PostgreSQL reales, comprobando 200/204/400/401/403, expiración, revocación, usuario desactivado y admin. El smoke `scripts/ci/smoke-auth.py` levanta la API real, registra un cliente de prueba, inicia sesión, accede a /me, recibe 403 en admin, termina sesión y confirma que el token ya no funciona.

Pruebas locales en PowerShell desde la raíz:

```powershell
git fetch origin
git switch --track origin/16-bkl-016-login-logout-autorizacion
mvn -f apps/api/pom.xml clean test
# Para integración real, configurar previamente techstore_test y DB_TEST_PASSWORD:
mvn -f apps/api/pom.xml clean verify
```

La documentación de configuración de `techstore_test` se mantiene en `docs/backend/USER_ACCOUNTS.md`.

## Límites y amenazas pendientes

Es una **implementación académica para desarrollo local**, no una solución auditada de producción. Para un despliegue público hacen falta **HTTPS obligatorio**, almacenamiento seguro del Bearer en la aplicación cliente (preferir memoria y evitar `localStorage`), rate limiting y protección contra ataques de fuerza bruta, detección de comportamientos sospechosos, política de renovación y cierre global de sesiones, limpieza periódica de tokens expirados, auditoría, recuperación de contraseña y pruebas de seguridad. La respuesta de login utiliza un error genérico, pero **aún no se garantiza resistencia a enumeración por diferencias de tiempo**.

No se crean cuentas ADMIN mediante el registro público. Para probar ADMIN en integración, `AuthenticationIT` introduce una cuenta exclusivamente en la base aislada de pruebas. No se deben dejar credenciales administrativas de ejemplo en Flyway ni en Git.

**No implica implementación de pedidos, checkout, panel administrativo, JWT, cookies ni autenticación del frontend.** Estas funciones se planifican en sus BKL correspondientes.
