"""HTTP smoke: pedido PENDING desde carrito real; sin datos de tarjeta."""
import json
from decimal import Decimal
from uuid import uuid4
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:8080"
PASS = "ClaveDeCheckout2026"
ADDRESS = {
    "province": "San José", "canton": "Central", "district": "Carmen",
    "details": "Casa azul frente al parque central",
    "recipientName": "Cliente de CI", "phone": "8888-1111"
}

def call(method, path, payload=None, bearer=None, status=200):
    headers = {}
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    data = None if payload is None else json.dumps(payload).encode()
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=10) as res:
            code, raw, response_headers = res.status, res.read(), res.headers
    except HTTPError as err:
        code, raw, response_headers = err.code, err.read(), err.headers
    assert code == status, (method, path, code, status, raw[:160])
    if code == 204:
        return None, response_headers
    return json.loads(raw, parse_float=Decimal), response_headers

anonymous, _ = call("POST", "/api/orders", {"shippingAddress": ADDRESS}, status=401)
assert anonymous["code"] == "AUTHENTICATION_REQUIRED"
email = "it.checkout." + uuid4().hex + "@techstore.test"
call("POST", "/api/auth/register", {
    "displayName": "Cliente de CI", "email": email, "password": PASS
}, status=201)
session, _ = call("POST", "/api/auth/login", {
    "email": email, "password": PASS
})
bearer = session["accessToken"]
empty, _ = call("POST", "/api/orders", {"shippingAddress": ADDRESS}, bearer, 422)
assert empty["code"] == "EMPTY_CART"
catalog, _ = call("GET", "/api/products")
item = next(x for x in catalog["items"] if x["sku"] == "DEMO-MOUSE-01")
assert item["stock"] >= 2
call("POST", "/api/cart/items", {"productId": item["id"], "quantity": 2}, bearer)
order, headers = call("POST", "/api/orders", {"shippingAddress": ADDRESS}, bearer, 201)
assert order["status"] == "PENDING" and order["paymentMode"] == "SIMULATED"
assert order["items"][0]["quantity"] == 2
assert Decimal(str(order["items"][0]["unitPriceCrc"])) == Decimal(str(item["priceCrc"]))
assert Decimal(str(order["totals"]["subtotalCrc"])) == 2 * Decimal(str(item["priceCrc"]))
assert "no-store" in headers.get("Cache-Control", "")
assert headers.get("Location") == "/api/orders/" + str(order["id"])
product, _ = call("GET", "/api/products/" + str(item["id"]))
assert product["stock"] == item["stock"] - 2
cart, _ = call("GET", "/api/cart", bearer=bearer)
assert cart["items"] == []
retry, _ = call("POST", "/api/orders", {"shippingAddress": ADDRESS}, bearer, 422)
assert retry["code"] == "EMPTY_CART"
print("Smoke checkout OK: pedido creado, stock reservado y sin venta duplicada")
