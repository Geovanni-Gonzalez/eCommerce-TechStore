"""Smoke HTTP de cotización, impuestos y GoF Strategy (BKL-032).

Usa PostgreSQL efímero de CI y un cliente aleatorio. No imprime secretos
y no crea órdenes/pagos. Aplica reglas ilustrativas, no fiscales productivas.
"""
import json
from decimal import Decimal, ROUND_HALF_UP
from uuid import uuid4
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:8080"
PASSWORD = "ClaveCheckoutSmoke2026"


def request(method, path, payload=None, token=None, status=200):
    headers = {}
    data = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    req = Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=10) as response:
            actual = response.status
            response_headers = response.headers
            raw = response.read()
    except HTTPError as exception:
        actual = exception.code
        response_headers = exception.headers
        raw = exception.read()

    assert actual == status, (method, path, actual, status)
    if actual == 204:
        assert not raw
        return None, response_headers

    media_type = ("application/problem+json"
                  if actual >= 400 else "application/json")
    assert media_type in response_headers.get("Content-Type", "")
    assert PASSWORD not in raw.decode("utf-8")
    return json.loads(raw, parse_float=Decimal), response_headers


anonymous, _ = request("GET", "/api/checkout/quote", status=401)
assert anonymous["code"] == "AUTHENTICATION_REQUIRED"

email = f"it.quote.smoke.{uuid4().hex}@techstore.test"
request("POST", "/api/auth/register", {
    "displayName": "Cliente cotización",
    "email": email,
    "password": PASSWORD
}, status=201)
logged, _ = request("POST", "/api/auth/login", {
    "email": email, "password": PASSWORD
}, status=200)
token = logged["accessToken"]

empty, _ = request("GET", "/api/checkout/quote", token=token, status=422)
assert empty["code"] == "EMPTY_CART"

products, _ = request("GET", "/api/products")
valid = [p for p in products["items"] if p["stock"] >= 1]
assert valid, "Se requiere un artículo del catálogo con stock positivo"
product = valid[0]

request("POST", "/api/cart/items", {
    "productId": product["id"],
    "quantity": 1
}, token=token)

quote, headers = request("GET", "/api/checkout/quote", token=token)
assert "no-store" in headers.get("Cache-Control", "")
assert quote["estimateOnly"] is True
assert quote["totalItems"] == 1
assert quote["shippingPolicy"] == "FREE_OVER_THRESHOLD"
totals = quote["totals"]

subtotal = Decimal(str(product["priceCrc"]))
tax = (subtotal * Decimal("0.13")).quantize(
    Decimal("0.01"), rounding=ROUND_HALF_UP)
shipping = (Decimal("0.00") if subtotal >= Decimal("30000.00")
            else Decimal("2500.00"))
assert Decimal(str(totals["subtotalCrc"])) == subtotal
assert Decimal(str(totals["taxCrc"])) == tax
assert Decimal(str(totals["shippingCrc"])) == shipping
assert Decimal(str(totals["totalCrc"])) == subtotal + shipping + tax

# Nunca confía en userId, importe o política enviados por query string.
injected, _ = request(
    "GET", "/api/checkout/quote?totalCrc=1&userId=999&shippingPolicy=FIXED",
    token=token)
assert injected == quote

request("POST", "/api/auth/logout", token=token, status=204)
revoked, _ = request("GET", "/api/checkout/quote", token=token, status=401)
assert revoked["code"] == "AUTHENTICATION_REQUIRED"

print("Smoke cotización OK: Strategy, impuestos CRC, autenticación y sin pedidos")
