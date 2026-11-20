# BKL-015 — Registro seguro de clientes

## Contrato público

`POST /api/auth/register` acepta JSON y responde HTTP `201 Created` con el esquema `User` definido en `docs/api/openapi.json`:

```json
{
  "displayName": "Ana Pérez",
  "email": "ana@example.com",
  "password": "EjemploSegura2026"
}
```

Respuesta (`201`):

```json
{
  "id": 1,
  "displayName": "Ana Pérez",
  "email": "ana@example.com",
  "role": "CLIENT"
}
```

Los valores son ejemplos, no credenciales de producción. **No se devuelve la contraseña ni su hash** y tampoco se crea una sesión: el inicio de sesión pertenece a BKL-016.

### Validaciones de registro

- `displayName`: requerido, texto de 2–120 caracteres, se recortan espacios al principio y al final; no se admiten caracteres de control.
- `email`: requerido, correo válido de hasta 254 caracteres; se normaliza a minúsculas, se quitan espacios externos y se impide el duplicado mediante el índice UNIQUE de PostgreSQL.
- `password`: requerida, texto de 8–128 caracteres sin caracteres de control. **BCrypt tiene un límite de 72 bytes**: se rechazan contraseñas que lo superen, incluso si tienen menos de 128 caracteres, para impedir truncamientos silenciosos.
- No se aceptan campos adicionales, incluidos `role` o `active` (el contrato OpenAPI especifica `additionalProperties: false`). Todos los registros públicos crean exclusivamente el rol interno `CLIENTE`.
- HTTP `400` para campos ausentes, inválidos o JSON ilegible; HTTP `409` para correo ya registrado. Los errores se envían como `application/problem+json`, con `code` estable y, en las validaciones, `errors: [{field,message}]`.
- El mensaje de error nunca debe incluir valores de contraseñas, hashes ni detalles SQL.

### Persistencia y evolución

BKL-014 creó `users` en Flyway V2. **V3** añade `display_name VARCHAR(120) NOT NULL` y restricciones SQL; a las cuentas existentes se asigna temporalmente el nombre neutro `Cliente`, para que la migración sea compatible con datos anteriores. No editar V1/V2 ya aplicadas.

El rol interno **`CLIENTE`** se expone públicamente como **`CLIENT`**, según OpenAPI. El cliente nunca puede solicitar `ADMIN`: `JdbcUserAccountRepository.createClient` fija el rol directamente en SQL. `PasswordHasher` abstrae BCrypt y `RegistrationService` coordina validación, normalización, hashing e inserción, manteniendo SOLID (SRP/DIP) y GRASP (controlador, experto, bajo acoplamiento).

### Pruebas

`RegistrationServiceTest` cubre validación, duplicados y carrera sobre índice único. `RegistrationIT` emplea Spring MVC, Flyway y PostgreSQL `techstore_test`, verifica que el hash BCrypt real se almacena sin mostrar secretos en JSON y que se rechaza un `role=ADMIN`. `scripts/ci/smoke-register.py` realiza solicitudes HTTP reales contra el JAR iniciado con una base efímera.

```powershell
git fetch origin
git switch --track origin/15-bkl-015-registro-seguro-clientes
mvn -f apps/api/pom.xml clean test
# Para integración, crear/configurar la base aislada techstore_test,
# establecer DB_TEST_PASSWORD y ejecutar:
mvn -f apps/api/pom.xml clean verify
```

En un entorno de desarrollo con API iniciada, ejemplo PowerShell:

```powershell
$payload = @{ displayName = "Ana Pérez"; email = "ana@example.com"; password = "EjemploSegura2026" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body $payload
```

**Actualización BKL-016:** inicio/cierre de sesión, Bearer opaco revocable y autorización por roles ya se implementaron; consultar [AUTHENTICATION.md](AUTHENTICATION.md). Siguen pendientes protecciones avanzadas contra abuso, recuperación de contraseña y autenticación de la interfaz Next.js.
