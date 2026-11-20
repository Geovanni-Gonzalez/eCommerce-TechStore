"""Verifica HTTP real de TechStore con la API y PostgreSQL iniciados en CI.

Requiere haber cargado scripts/dev/seed-catalog.sql en la base de pruebas.
Usa exclusivamente la biblioteca estándar de Python.
"""
import json
from urllib.error import HTTPError
from urllib.request import urlopen

BASE = "http://127.0.0.1:8080"


def get(path, expected_status=200):
    try:
        with urlopen(BASE + path, timeout=5) as response:
            status = response.status
            body = json.load(response)
            content_type = response.headers.get("Content-Type", "")
    except HTTPError as exc:
        status = exc.code
        content_type = exc.headers.get("Content-Type", "")
        body = json.load(exc)

    assert status == expected_status, (path, status, body)
    expected_type = "application/problem+json" if expected_status >= 400 else "application/json"
    assert expected_type in content_type, (path, content_type)
    return body


listing = get("/api/products?page=0&size=1")
assert listing["totalElements"] == 2, listing
assert listing["totalPages"] == 2, listing
assert listing["page"] == 0 and listing["size"] == 1, listing
assert len(listing["items"]) == 1, listing

second = get("/api/products?page=1&size=1")
assert len(second["items"]) == 1, second
visible = listing["items"] + second["items"]
assert {p["sku"] for p in visible} == {"DEMO-MOUSE-01", "DEMO-KEY-01"}, visible
assert all(p["active"] for p in visible), visible

product = get(f"/api/products/{visible[0]['id']}")
assert product["sku"] == visible[0]["sku"], product
assert product["priceCrc"] >= 0, product

missing = get("/api/products/999999999", 404)
assert missing["status"] == 404, missing
assert missing["code"] == "PRODUCT_NOT_FOUND", missing

invalid = get("/api/products?page=-1", 400)
assert invalid["status"] == 400, invalid
assert invalid["code"] == "VALIDATION_ERROR", invalid

print("Smoke HTTP OK: lista paginada, detalle, exclusión de inactivos, 400 y 404")
