"""Smoke HTTP real para login/logout y autorización Bearer revocable.

PostgreSQL de CI efímero. Nunca imprime credenciales ni tokens.
"""
import json
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import uuid4

BASE = "http://127.0.0.1:8080"
PASSWORD = "ClaveSeguraCI2026"


def request(method, path, payload=None, token=None, expected=200):
    headers = {}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token is not None:
        headers["Authorization"] = "Bearer " + token
    call = Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urlopen(call, timeout=10) as response:
            status = response.status
            media = response.headers.get("Content-Type", "")
            raw = response.read()
    except HTTPError as failure:
        status = failure.code
        media = failure.headers.get("Content-Type", "")
        raw = failure.read()

    assert status == expected, (method, path, status, expected)
    if status == 204:
        assert not raw
        return None
    expected_type = "application/problem+json" if status >= 400 else "application/json"
    assert expected_type in media, (path, status, media)
    obj = json.loads(raw)
    assert PASSWORD not in json.dumps(obj), "La respuesta contiene una contraseña"
    return obj


email = f"it.smoke.auth.{uuid4().hex}@techstore.test"
registered = request("POST", "/api/auth/register", {
    "displayName": "Autenticación CI",
    "email": email,
    "password": PASSWORD,
}, expected=201)
assert registered["role"] == "CLIENT"

no_token = request("GET", "/api/auth/me", expected=401)
assert no_token["code"] == "AUTHENTICATION_REQUIRED"
wrong_login = request("POST", "/api/auth/login", {
    "email": email, "password": "ContraseñaIncorrecta"}, expected=401)
assert wrong_login["code"] == "INVALID_CREDENTIALS"

session = request("POST", "/api/auth/login", {
    "email": email.upper(), "password": PASSWORD,
})
token = session["accessToken"]
assert session["tokenType"] == "Bearer"
assert session["user"]["role"] == "CLIENT"
assert len(token) == 43
assert "passwordHash" not in session["user"]

me = request("GET", "/api/auth/me", token=token)
assert me["id"] == registered["id"]
assert me["email"] == email

denied = request("GET", "/api/admin/check", token=token, expected=403)
assert denied["code"] == "ACCESS_DENIED"

request("POST", "/api/auth/logout", token=token, expected=204)
revoked = request("GET", "/api/auth/me", token=token, expected=401)
assert revoked["code"] == "AUTHENTICATION_REQUIRED"
request("POST", "/api/auth/logout", token=token, expected=401)

public = request("GET", "/api/health")
assert public["status"] == "UP"

print("Smoke auth OK: login, cliente, 401/403, logout y revocación inmediata")
