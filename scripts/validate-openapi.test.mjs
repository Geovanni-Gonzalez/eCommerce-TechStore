import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { validateContract } from "./validate-openapi.mjs";

const original = JSON.parse(readFileSync("docs/api/openapi.json", "utf8"));
const clone = () => structuredClone(original);

test("el contrato actual es consistente", () => {
  const result = validateContract(original);
  assert.equal(result.operationCount, 17);
  assert.equal(result.proposedCount, 2);
});

test("rechaza referencias inexistentes", () => {
  const invalid = clone();
  invalid.paths["/api/products"].get.responses["200"].content["application/json"].schema.$ref =
    "#/components/schemas/NoExiste";
  assert.throws(() => validateContract(invalid), /Referencia no resuelta/);
});

test("rechaza rutas privadas sin autorización", () => {
  const invalid = clone();
  delete invalid.paths["/api/orders"].post.security;
  assert.throws(() => validateContract(invalid), /Crear pedido exige Bearer/);
});

test("rechaza respuestas de error sin Problem Details", () => {
  const invalid = clone();
  delete invalid.paths["/api/products"].get.responses["400"].content;
  assert.throws(() => validateContract(invalid), /sin Problem Details/);
});

test("rechaza operationId repetido", () => {
  const invalid = clone();
  invalid.paths["/api/products"].get.operationId =
    invalid.paths["/api/products/{id}"].get.operationId;
  assert.throws(() => validateContract(invalid), /operationId duplicado/);
});

test("el registro seguro se mantiene marcado como implementado", () => {
  const invalid = clone();
  invalid.paths["/api/auth/register"].post["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(invalid), /El registro seguro debe estar marcado como implementado/);
});

test("rechaza documentar los tokens como JWT cuando son opacos", () => {
  const invalid = clone();
  invalid.components.securitySchemes.bearerAuth.bearerFormat = "JWT";
  assert.throws(() => validateContract(invalid), /Bearer opaco revocable/);
});

test("rechaza ruta admin sin autorización Bearer", () => {
  const invalid = clone();
  delete invalid.paths["/api/admin/check"].get.security;
  assert.throws(() => validateContract(invalid), /Ruta privada sin Bearer/);
});

test("rechaza declarar login pendiente", () => {
  const invalid = clone();
  invalid.paths["/api/auth/login"].post["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(invalid), /Debe estar implementado: post/);
});

test("rechaza carrito sin Bearer y documentarlo como planificado", () => {
  const withoutBearer = clone();
  delete withoutBearer.paths["/api/cart"].get.security;
  assert.throws(() => validateContract(withoutBearer), /Carrito sin Bearer/);
  const planned = clone();
  planned.paths["/api/cart/items"].post["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(planned), /Carrito pendiente/);
});

test("cotización solo para CLIENT, disponible y con DTO exacto", () => {
  const withoutBearer = clone();
  delete withoutBearer.paths["/api/checkout/quote"].get.security;
  assert.throws(() => validateContract(withoutBearer), /Cotización debe exigir Bearer/);

  const planned = clone();
  planned.paths["/api/checkout/quote"].get["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(planned), /Cotización debe estar implementada/);

  const wrongDto = clone();
  wrongDto.paths["/api/checkout/quote"].get.responses["200"]
    .content["application/json"].schema.$ref = "#/components/schemas/Order";
  assert.throws(() => validateContract(wrongDto), /Cotización debe devolver DTO/);
});

test("POST orders requiere Bearer y no acepta precios del navegador", () => {
  const planned = clone();
  planned.paths["/api/orders"].post["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(planned),
    /La creación transaccional de pedidos debe estar implementada/);

  const withPrice = clone();
  withPrice.components.schemas.CreateOrderRequest.properties.totalCrc = {type: "number"};
  assert.throws(() => validateContract(withPrice),
    /Nunca aceptar precios, productos o propietario/);
});
