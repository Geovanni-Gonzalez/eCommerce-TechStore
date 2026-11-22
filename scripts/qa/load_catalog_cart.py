#!/usr/bin/env python3
"""BKL-057: carga GET local y acotada sin dependencias, PII ni escrituras.
USO SOLO contra API local conectada a PostgreSQL de prueba.
"""
from __future__ import annotations
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import math
import os
import statistics
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


def p95(samples_ms: list[float]) -> float:
    if not samples_ms:
        raise ValueError("Se necesita al menos una muestra")
    values = sorted(samples_ms)
    return values[max(0, math.ceil(len(values) * .95) - 1)]


def require_local_api(url: str) -> str:
    parsed = urlparse(url)
    if (parsed.scheme not in ("http", "https")
            or parsed.hostname not in ("localhost", "127.0.0.1", "::1")
            or parsed.username or parsed.password or parsed.query or parsed.fragment
            or (parsed.path not in ("", "/"))):
        raise ValueError("Solo se permite la API local; nunca producción ni credenciales en URL")
    return url.rstrip("/")


def probe(base: str, path: str, bearer: str | None) -> tuple[float, int]:
    headers = {"Accept": "application/json"}
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    request = Request(base + path, headers=headers, method="GET")
    start = time.perf_counter()
    try:
        with urlopen(request, timeout=8) as response:
            response.read(1024)  # no conservar cuerpos ni información de clientes
            status = response.status
    except HTTPError as error:
        status = error.code
    except (URLError, TimeoutError):
        status = 0
    return (time.perf_counter() - start) * 1000, status


def run_case(base: str, path: str, label: str, token: str | None,
             count: int, concurrency: int, budget_ms: float) -> bool:
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(probe, base, path, token) for _ in range(count)]
        results = [future.result() for future in as_completed(futures)]
    times = [elapsed for elapsed, _ in results]
    errors = sum(status != 200 for _, status in results)
    p = p95(times)
    print(f"{label}: {count} solicitudes; errores={errors}; "
          f"p50={statistics.median(times):.1f}ms; p95={p:.1f}ms; "
          f"objetivo={budget_ms:.1f}ms")
    return errors == 0 and p <= budget_ms


def main() -> int:
    parser = argparse.ArgumentParser(description="Carga local de solo lectura de TechStore")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--requests", type=int, default=40)
    parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--catalog-p95-ms", type=float, default=500)
    parser.add_argument("--cart-p95-ms", type=float, default=700)
    args = parser.parse_args()
    if not (1 <= args.requests <= 300 and 1 <= args.concurrency <= 8
            and args.catalog_p95_ms > 0 and args.cart_p95_ms > 0):
        parser.error("Límites inválidos: 1–300 solicitudes y 1–8 hilos")
    base = require_local_api(args.base_url)
    if os.environ.get("TECHSTORE_LOAD_TEST_DB_CONFIRMED") != "yes":
        parser.error("Confirma base de datos TEST: TECHSTORE_LOAD_TEST_DB_CONFIRMED=yes")
    token = os.environ.get("TECHSTORE_LOAD_BEARER_TOKEN", "")
    if not token or any(c.isspace() for c in token):
        parser.error("Se requiere Bearer CLIENT de prueba en TECHSTORE_LOAD_BEARER_TOKEN")
    catalog_ok = run_case(base, "/api/products?page=0&size=12", "catalogo",
                          None, args.requests, args.concurrency, args.catalog_p95_ms)
    cart_ok = run_case(base, "/api/cart", "carrito",
                       token, args.requests, args.concurrency, args.cart_p95_ms)
    return 0 if catalog_ok and cart_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
