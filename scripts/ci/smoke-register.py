"""Smoke REST real para BKL-015 con PostgreSQL efímero y Spring Boot iniciado.

Genera un correo nuevo en cada ejecución y no imprime ni registra contraseñas.
"""
import json
from uuid import uuid4
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:8080"
PASSWORD = "PruebaSegura2026"


def register(payload, expected_status):
    body = json.dumps(payload).encode("utf-8")
    request = Request(
        BASE + "/api/auth/register",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=10) as response:
            status = response.status
            media_type = response.headers.get("Content-Type", "")
            data = json.load(response)
    except HTTPError as error:
        status = error.code
        media_type = error.headers.get("Content-Type", "")
        data = json.load(error)

    assert status == expected_status, (status, expected_status, data)
    required_type = "application/json" if status == 201 else "application/problem+json"
    assert required_type in media_type, media_type
    assert PASSWORD not in json.dumps(data), "Se filtró una contraseña"
    return data


email = f"it.smoke.{uuid4().hex}@techstore.test"
registered = register(
    {"displayName": "  Cliente de CI  ", "email": email.upper(),
     "password": PASSWORD},
    201,
)
assert registered["displayName"] == "Cliente de CI", registered
assert registered["email"] == email, registered
assert registered["role"] == "CLIENT", registered
assert isinstance(registered["id"], int), registered
assert set(registered) == {"id", "displayName", "email", "role"}, registered

duplicate = register(
    {"displayName": "Mismo correo", "email": email, "password": PASSWORD}, 409
)
assert duplicate["code"] == "EMAIL_ALREADY_REGISTERED", duplicate

invalid = register(
    {"displayName": "A", "email": "no-valido", "password": "corta"}, 400
)
assert invalid["code"] == "VALIDATION_ERROR", invalid

forbidden_role = register(
    {"displayName": "Impostor", "email": "admin." + email,
     "password": PASSWORD, "role": "ADMIN"},
    400,
)
assert forbidden_role["code"] == "VALIDATION_ERROR", forbidden_role

print("Smoke registro OK: 201, 409, 400, rol CLIENT y salida sin secretos")
