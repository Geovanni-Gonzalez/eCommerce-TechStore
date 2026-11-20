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

  assert.equal(operationCount, 20, "Se esperan 20 operaciones de API");
  assert.equal(doc.paths["/api/auth/register"].post["x-implementation-status"], "implemented",
    "El registro seguro debe estar marcado como implementado");
  for (const [path, method] of [
    ["/api/auth/login", "post"],
    ["/api/auth/logout", "post"],
    ["/api/auth/me", "get"],
    ["/api/admin/check", "get"]
  ]) {
    assert.equal(doc.paths[path][method]["x-implementation-status"], "implemented",
      `Debe estar implementado: ${method} ${path}`);
  }
  for (const [route, method] of [["/api/cart", "get"], ["/api/cart", "delete"],
    ["/api/cart/items", "post"], ["/api/cart/items/{productId}", "patch"],
    ["/api/cart/items/{productId}", "delete"]]) {
    const op = doc.paths[route]?.[method];
    assert.ok(op, `Ruta de carrito faltante: ${method} ${route}`);
    assert.equal(op["x-implementation-status"], "implemented", `Carrito pendiente: ${route}`);
    assert.deepEqual(op.security, [{ bearerAuth: [] }], `Carrito sin Bearer: ${route}`);
  }
  const quote = doc.paths["/api/checkout/quote"]?.get;
  assert.ok(quote, "Falta endpoint de cotización");
  assert.equal(quote["x-implementation-status"], "implemented",
    "Cotización debe estar implementada");
  assert.deepEqual(quote.security, [{ bearerAuth: [] }],
    "Cotización debe exigir Bearer");
  assert.equal(quote.responses["200"]?.content?.["application/json"]?.schema?.$ref,
    "#/components/schemas/CheckoutQuote", "Cotización debe devolver DTO");
  assert.ok(quote.responses["409"] && quote.responses["422"],
    "Cotización debe documentar carrito vacío e indisponible");
  const createdOrder = doc.paths["/api/orders"]?.post;
  assert.equal(createdOrder?.["x-implementation-status"], "implemented",
    "La creación transaccional de pedidos debe estar implementada");
  assert.deepEqual(createdOrder.security, [{ bearerAuth: [] }],
    "Crear pedido exige Bearer");
  assert.equal(createdOrder.requestBody?.content?.["application/json"]?.schema?.$ref,
    "#/components/schemas/CreateOrderRequest",
    "El pedido debe requerir el body del checkout");
  assert.deepEqual(Object.keys(doc.components.schemas.CreateOrderRequest?.properties ?? {}),
    ["shippingAddress"],
    "Nunca aceptar precios, productos o propietario en creación de pedido");
  const cancellation = doc.paths["/api/orders/{id}/cancel"]?.post;
  assert.equal(cancellation?.["x-implementation-status"], "implemented",
    "La cancelación de reservas debe estar implementada");
  assert.deepEqual(cancellation.security, [{ bearerAuth: [] }],
    "Cancelar pedido exige Bearer");
  assert.ok(cancellation.responses["204"] && cancellation.responses["404"]
    && cancellation.responses["409"], "Faltan estados de cancelación");
  const adminStatus = doc.paths["/api/admin/orders/{id}/status"]?.patch;
  const adminHistory = doc.paths["/api/admin/orders/{id}/history"]?.get;
  assert.equal(adminStatus?.["x-implementation-status"], "implemented",
    "BKL-045: transición administrativa debe estar implementada");
  assert.equal(adminHistory?.["x-implementation-status"], "implemented",
    "BKL-045: historial debe estar implementado");
  assert.deepEqual(adminStatus.security, [{ bearerAuth: [] }],
    "BKL-045: cambio administrativo exige Bearer");
  assert.deepEqual(adminHistory.security, [{ bearerAuth: [] }],
    "BKL-045: historial administrativo exige Bearer");
  assert.equal(adminHistory.responses["200"]?.content?.["application/json"]?.schema?.items?.$ref,
    "#/components/schemas/OrderStatusHistoryEntry",
    "BKL-045: el historial requiere DTO auditable");
  assert.equal(adminStatus.requestBody?.content?.["application/json"]?.schema?.$ref,
    "#/components/schemas/UpdateOrderStatusRequest",
    "BKL-045: solo se admite body de estado");
  assert.equal(doc.paths["/api/orders"].get["x-implementation-status"], "planned",
    "Historial de pedidos sigue pendiente");
  assert.equal(doc.paths["/api/orders/{id}"].get["x-implementation-status"], "planned",
    "Detalle HTTP de pedido sigue pendiente");
  assert.equal(proposedCount, 2, "Solo las lecturas de pedidos están planificadas");
  for (const route of ["/api/products", "/api/products/{id}"]) {
    assert.equal(doc.paths[route].get["x-implementation-status"], "implemented",
      `Catálogo aún no marcado como implementado: ${route}`);
  }
  assert.equal(doc.paths["/api/health"].get["x-implementation-status"], "implemented");

  assert.ok(doc.components.schemas.Problem, "Falta esquema de errores");
  assert.ok(doc.components.securitySchemes.bearerAuth, "Falta seguridad para rutas privadas");
  assert.equal(doc.components.securitySchemes.bearerAuth.bearerFormat, "Opaque",
    "El contrato debe declarar Bearer opaco revocable, no JWT");
  for (const [path, method] of [
    ["/api/auth/logout", "post"],
    ["/api/auth/me", "get"],
    ["/api/admin/check", "get"]
  ]) {
    assert.deepEqual(doc.paths[path][method].security, [{ bearerAuth: [] }],
      `Ruta privada sin Bearer: ${method} ${path}`);
  }
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
