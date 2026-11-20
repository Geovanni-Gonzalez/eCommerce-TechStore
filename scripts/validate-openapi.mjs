/**
 * Validación estructural ejecutable del contrato OpenAPI del prototipo.
 * Usa únicamente Node.js, sin dependencias ni conexión a Internet.
 * No pretende sustituir validadores externos de conformidad OpenAPI.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

export function validateContract(doc) {
  assert.equal(doc.openapi, "3.0.3", "Debe usar OpenAPI 3.0.3");
  assert.ok(doc.info?.title && doc.info?.version, "Faltan metadatos");
  assert.ok(Array.isArray(doc.servers) && doc.servers.length > 0);
  assert.ok(doc.paths && typeof doc.paths === "object");
  assert.ok(doc.components?.schemas && typeof doc.components.schemas === "object");

  const methods = new Set(["get", "post", "put", "patch", "delete"]);
  const operationIds = new Set();
  const tags = new Set((doc.tags ?? []).map((tag) => tag.name));
  let operationCount = 0;
  let proposedCount = 0;

  function visit(value, location) {
    if (Array.isArray(value)) {
      value.forEach((child, index) => visit(child, `${location}[${index}]`));
      return;
    }
    if (!value || typeof value !== "object") return;
    if (typeof value.$ref === "string") {
      assert.match(value.$ref, /^#\/components\/(schemas|securitySchemes)\/[A-Za-z][A-Za-z0-9]*$/, `Referencia no soportada en ${location}`);
      const [, , group, name] = value.$ref.split("/");
      assert.ok(doc.components[group]?.[name], `Referencia no resuelta: ${value.$ref}`);
    }
    for (const [key, child] of Object.entries(value)) {
      visit(child, `${location}.${key}`);
    }
  }

  visit(doc, "OpenAPI");

  for (const [path, operations] of Object.entries(doc.paths)) {
    assert.ok(path.startsWith("/api/"), `Ruta inesperada: ${path}`);
    for (const [method, operation] of Object.entries(operations)) {
      assert.ok(methods.has(method), `Método HTTP inesperado: ${method}`);
      operationCount++;
      assert.ok(operation.summary && operation.operationId, `Operación incompleta: ${method} ${path}`);
      assert.ok(!operationIds.has(operation.operationId), `operationId duplicado: ${operation.operationId}`);
      operationIds.add(operation.operationId);
      assert.ok(operation.tags?.every((tag) => tags.has(tag)), `Tag desconocido: ${path}`);
      assert.ok(Object.keys(operation.responses ?? {}).some((status) => /^2\d\d$/.test(status)), `Sin respuesta 2xx: ${path}`);
      const status = operation["x-implementation-status"];
      assert.ok(["planned", "implemented"].includes(status), `Sin estado de implementación: ${path}`);
      if (status === "planned") proposedCount++;

      for (const [, parameter] of [...path.matchAll(/\{([^}]+)\}/g)]) {
        assert.ok((operation.parameters ?? []).some((p) =>
          p.name === parameter && p.in === "path" && p.required === true
        ), `Parámetro de ruta no documentado: ${path}`);
      }
      for (const [code, response] of Object.entries(operation.responses)) {
        assert.ok(response.description, `Respuesta ${code} sin descripción: ${path}`);
        if (/^[45]\d\d$/.test(code)) {
          assert.ok(response.content?.["application/problem+json"]?.schema?.$ref === "#/components/schemas/Problem",
            `Error ${code} sin Problem Details: ${path}`);
        }
      }
    }
  }

  assert.equal(operationCount, 8, "Se esperan 8 operaciones iniciales");
  assert.equal(proposedCount, 5, "Health y dos rutas del catálogo están implementados");
  for (const route of ["/api/products", "/api/products/{id}"]) {
    assert.equal(doc.paths[route].get["x-implementation-status"], "implemented",
      `Catálogo aún no marcado como implementado: ${route}`);
  }
  assert.equal(doc.paths["/api/health"].get["x-implementation-status"], "implemented");
  assert.ok(doc.components.schemas.Problem, "Falta esquema de errores");
  assert.ok(doc.components.securitySchemes.bearerAuth, "Falta seguridad para rutas privadas");
  for (const path of ["/api/orders", "/api/orders/{id}"]) {
    for (const operation of Object.values(doc.paths[path])) {
      assert.deepEqual(operation.security, [{ bearerAuth: [] }], `Pedido sin autorización: ${path}`);
    }
  }
  return { operationCount, proposedCount, schemas: Object.keys(doc.components.schemas).length };
}

const invokedDirectly = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invokedDirectly) {
  try {
    const source = readFileSync(resolve("docs/api/openapi.json"), "utf8");
    const result = validateContract(JSON.parse(source));
    console.log(`Contrato OpenAPI válido a nivel estructural: ${result.operationCount} operaciones, ${result.schemas} esquemas, ${result.proposedCount} rutas pendientes.`);
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
}
