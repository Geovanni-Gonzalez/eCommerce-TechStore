"""Prueba HTTP de extremo a extremo del carrito con auth y PostgreSQL reales.

El pipeline carga fixtures de catálogo; genera clientes efímeros y no imprime
contraseñas ni tokens.
"""
import json
from uuid import uuid4
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:8080"
PASSWORD = "ClaveTestCarrito2026"


def call(method, path, payload=None, token=None, expected=200):
    headers = {}
    data = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode("utf-8")
    if token is not None:
        headers["Authorization"] = "Bearer " + token
    req = Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=10) as response:
            status, media, raw = response.status, response.headers.get("Content-Type", ""), response.read()
    except HTTPError as error:
        status, media, raw = error.code, error.headers.get("Content-Type", ""), error.read()
    assert status == expected, (method, path, status, expected)
    if status == 204:
        assert not raw
        return None
    content_type = "application/problem+json" if status >= 400 else "application/json"
    assert content_type in media
    data = json.loads(raw)
    assert PASSWORD not in json.dumps(data)
    return data


def create_account():
    email = "it.cart.smoke." + uuid4().hex + "@techstore.test"
    call("POST", "/api/auth/register", {
        "displayName": "Cliente de carrito", "email": email, "password": PASSWORD
    }, expected=201)
    result = call("POST", "/api/auth/login", {"email": email, "password": PASSWORD})
    return result["accessToken"]


products = call("GET", "/api/products", expected=200)["items"]
eligible = [p for p in products if p["stock"] >= 3]
assert eligible, "El catálogo de CI requiere un producto con >= 3 unidades"
product = eligible[0]
product_id = product["id"]
price = float(product["priceCrc"])

no_token = call("GET", "/api/cart", expected=401)
assert no_token["code"] == "AUTHENTICATION_REQUIRED"

a = create_account()
b = create_account()
empty = call("GET", "/api/cart", token=a)
assert empty["totalItems"] == 0 and empty["items"] == [] and not empty["checkoutReady"]

added = call("POST", "/api/cart/items", token=a, payload={
    "productId": product_id, "quantity": 1
})
assert added["totalItems"] == 1
assert added["subtotalCrc"] == price
assert added["checkoutReady"]

added = call("POST", "/api/cart/items", token=a, payload={
    "productId": product_id, "quantity": 1
})
assert added["totalItems"] == 2
updated = call("PATCH", f"/api/cart/items/{product_id}", token=a, payload={"quantity": 3})
assert updated["items"][0]["quantity"] == 3
assert updated["subtotalCrc"] == price * 3

other = call("GET", "/api/cart", token=b)
assert other["items"] == [] and other["totalItems"] == 0
not_found = call("PATCH", f"/api/cart/items/{product_id}", token=b,
                 payload={"quantity": 1}, expected=404)
assert not_found["code"] == "NOT_FOUND"

invalid = call("POST", "/api/cart/items", token=a, payload={
    "productId": product_id, "quantity": 1, "userId": 0, "priceCrc": 0
}, expected=400)
assert invalid["code"] == "VALIDATION_ERROR"

call("DELETE", f"/api/cart/items/{product_id}", token=a, expected=204)
assert call("GET", "/api/cart", token=a)["totalItems"] == 0
call("POST", "/api/cart/items", token=a, payload={
    "productId": product_id, "quantity": 1
})
call("DELETE", "/api/cart", token=a, expected=204)
assert call("GET", "/api/cart", token=a)["items"] == []
call("POST", "/api/auth/logout", token=a, expected=204)
assert call("GET", "/api/cart", token=a, expected=401)["code"] == "AUTHENTICATION_REQUIRED"

print("Smoke carrito OK: auth 401, aislamiento, sumar/actualizar/eliminar, subtotal y logout")
