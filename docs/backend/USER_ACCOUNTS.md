# BKL-014 — Cuentas y roles en PostgreSQL

## Alcance

Este issue prepara la persistencia de cuentas del prototipo TechStore para que BKL-015 implemente el registro y BKL-016 la autenticación. **No añade endpoints HTTP, tokens, sesiones ni un alta pública de administradores.** El contrato OpenAPI de auth continúa marcado como `planned`.

## Modelo y migración

Migración: `apps/api/src/main/resources/db/migration/V2__create_user_accounts.sql`.

| Columna | Regla |
| --- | --- |
| `id` | BIGINT, identidad y PK |
| `email` | Obligatorio, máximo 254 caracteres, normalizado en minúsculas sin espacios externos, índice UNIQUE |
| `password_hash` | Obligatorio, formato BCrypt válido (`$2a$`, `$2b$` o `$2y$`); nunca texto plano |
| `role` | Solo `CLIENTE` o `ADMIN`. `CLIENTE` predeterminado |
| `active` | Booleano, `TRUE` predeterminado |
| `created_at`, `updated_at` | `TIMESTAMPTZ` con valor inicial automático |

**Decisiones de seguridad:** El código normaliza correos con `UserEmail`. El dominio `PasswordHash` impide construir el tipo con una contraseña normal, y `BcryptPasswordHasher` usa salt aleatorio y coste 12 mediante `spring-security-crypto`. La base verifica el formato BCrypt para prevenir errores de almacenamiento comunes. **Esta verificación de formato no demuestra por sí sola que el hash sea auténtico:** todo alta debe pasar por el hasher del backend.

La operación `UserAccountRepository.createClient(email, passwordHash)` crea exclusivamente el rol `CLIENTE` sin aceptar el rol desde el exterior. Los administradores no deben poder crearse mediante el formulario de registro. El `toString()` del dominio oculta el hash y ningún controlador debe serializar directamente `UserAccount`; usar DTOs de salida que excluyan `passwordHash`.

## Arquitectura SOLID / GRASP

```text
Caso de uso de registro (BKL-015, pendiente)
        |                 |
        v                 v
PasswordHasher     UserAccountRepository
        ^                 ^
        |                 |
BcryptPasswordHasher  JdbcUserAccountRepository
                          |
                          v
                       PostgreSQL
```

- **SRP y alta cohesión:** normalización de correo, hashing y persistencia se distribuyen en clases distintas.
- **DIP y bajo acoplamiento:** los futuros servicios dependerán de `PasswordHasher` y `UserAccountRepository`, no del algoritmo BCrypt ni de JDBC.
- **Protección de privilegios:** `createClient` no permite elegir `ADMIN`.

## Pruebas

La CI usa PostgreSQL 17 efímero y una base dedicada `techstore_test`. Ejecuta Flyway V1 y V2 antes de probar JDBC con transacciones reversibles. `UserAccountIT` valida persistencia/lectura, correo único y normalizado, restricción del formato hash, roles válidos y timestamps. `UserDomainTest` verifica normalización, hashing con sal aleatoria y redacción de datos sensibles.

**Local (PowerShell):**

```powershell
docker compose -f infrastructure/compose.yaml up -d
docker exec techstore-postgres psql -U techstore -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='techstore_test'"
# Si no existe, créala una sola vez:
docker exec techstore-postgres createdb -U techstore techstore_test
$env:DB_TEST_PASSWORD = "techstore_dev_only"
mvn -f apps/api/pom.xml clean verify
```

La prueba de integración comprueba que la base se llame exactamente `techstore_test` antes de cualquier escritura. No ejecutar las pruebas contra producción. Para solo pruebas unitarias: `mvn -f apps/api/pom.xml clean test`.

## Futuro trabajo

- **BKL-015:** DTOs y endpoint de registro; validar contraseña, correo y manejo de duplicados; usar `PasswordHasher.hash` y crear solo `CLIENTE`.
- **BKL-016:** login/logout, comprobación `PasswordHasher.matches`, sesión/token y control de acceso a ADMIN. Nunca devolver hashes ni detalles de autenticación en errores.

No se modifica la migración V1 ni se crean usuarios o contraseñas de demostración en Flyway.
