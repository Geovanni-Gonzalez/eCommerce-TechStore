import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { validateContract } from "./validate-openapi.mjs";

const original = JSON.parse(readFileSync("docs/api/openapi.json", "utf8"));
const clone = () => structuredClone(original);

test("el contrato actual es consistente", () => {
  const result = validateContract(original);
  assert.equal(result.operationCount, 8);
  assert.equal(result.proposedCount, 7);
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
  assert.throws(() => validateContract(invalid), /Pedido sin autorización/);
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
