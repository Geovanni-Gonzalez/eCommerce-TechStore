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

  assert.equal(operationCount, 49, "Se esperan 49 operaciones de API");
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
  const paymentSubmit = doc.paths["/api/orders/{orderId}/payment-attempts"]?.post;
  const paymentGet = doc.paths["/api/orders/{orderId}/payment-attempts/{attemptId}"]?.get;
  assert.equal(paymentSubmit?.["x-implementation-status"], "implemented",
    "BKL-036: intento persistente debe estar implementado");
  assert.equal(paymentGet?.["x-implementation-status"], "implemented",
    "BKL-036: consulta de intento debe estar implementada");
  assert.deepEqual(paymentSubmit.security, [{ bearerAuth: [] }],
    "BKL-036: registrar intento requiere Bearer");
  assert.deepEqual(paymentGet.security, [{ bearerAuth: [] }],
    "BKL-036: consultar intento requiere Bearer");
  const idempotency = paymentSubmit.parameters?.find(parameter =>
    parameter.in === "header" && parameter.name === "Idempotency-Key");
  assert.equal(idempotency?.required, true,
    "BKL-036: Idempotency-Key es obligatorio");
  assert.equal(paymentSubmit.requestBody, undefined,
    "BKL-036: no aceptar montos, credenciales o body de pagos del navegador");
  assert.equal(paymentSubmit.responses["201"]?.content?.["application/json"]?.schema?.$ref,
    "#/components/schemas/PaymentAttempt",
    "BKL-036: el intento debe devolver DTO persistente");
  assert.ok(paymentSubmit.responses["200"] && paymentSubmit.responses["409"],
    "BKL-036: replay y conflicto de idempotencia son parte del contrato");
  const webhook = doc.paths["/api/payments/webhooks/sandbox"]?.post;
  assert.equal(webhook?.["x-implementation-status"], "implemented",
    "BKL-038: el webhook debe figurar implementado de forma condicional");
  assert.deepEqual(webhook.security, [{ webhookSignature: [] }],
    "BKL-038: el webhook exige autenticación HMAC, no Bearer");
  assert.equal(doc.components.securitySchemes.webhookSignature?.name,
    "X-TechStore-Webhook-Signature",
    "BKL-038: falta declaración de firma HMAC");
  assert.equal(webhook.parameters?.find(p =>
      p.name === "X-TechStore-Webhook-Timestamp" && p.in === "header")?.required,
    true, "BKL-038: timestamp firmado requerido");
  assert.equal(webhook.requestBody?.content?.["application/json"]?.schema?.$ref,
    "#/components/schemas/SandboxWebhookEvent",
    "BKL-038: body del evento debe ser estricto");
  assert.equal(webhook.responses["202"]?.content?.["application/json"]?.schema?.$ref,
    "#/components/schemas/VerifiedWebhookReceipt",
    "BKL-038: deduplicación debe devolver acuse de recepción");
  assert.ok(webhook.responses["200"] && webhook.responses["401"]
    && webhook.responses["409"],
    "BKL-038: contrato exige replay, autenticación y conflicto");
  const startSession = doc.paths["/api/orders/{orderId}/payment-sessions"]?.post;
  const previewSession = doc.paths["/api/payments/sandbox/sessions/preview"]?.post;
  const completeSession = doc.paths["/api/payments/sandbox/sessions/complete"]?.post;
  for (const op of [startSession, previewSession, completeSession]) {
    assert.equal(op?.["x-implementation-status"], "implemented",
      "BKL-037: las sesiones sandbox deben declararse implementadas condicionalmente");
  }
  assert.deepEqual(startSession.security, [{ bearerAuth: [] }],
    "BKL-037: solo CLIENT autenticado inicia pago");
  const startKey = startSession.parameters?.find(p =>
    p.in === "header" && p.name === "Idempotency-Key");
  assert.equal(startKey?.required, true,
    "BKL-037: inicio exige Idempotency-Key");
  assert.equal(startSession.requestBody, undefined,
    "BKL-037: rechazar monto, moneda, tarjeta y returnUrl del cliente");
  assert.deepEqual(previewSession.security, [],
    "BKL-037: preview autentica con token de sesión, no Bearer");
  assert.deepEqual(completeSession.security, [],
    "BKL-037: complete autentica con token de sesión, no Bearer");
  for (const op of [previewSession, completeSession]) {
    assert.equal(op.requestBody?.content?.["application/json"]?.schema?.$ref,
      "#/components/schemas/SandboxSessionTokenRequest",
      "BKL-037: solo se admite token efímero");
  }
  assert.equal(startSession.responses["201"]?.content?.["application/json"]?.schema?.$ref,
    "#/components/schemas/SandboxSessionStarted",
    "BKL-037: el inicio debe entregar URL de redirección");
  assert.equal(completeSession.responses["200"]?.content?.["application/json"]?.schema?.$ref,
    "#/components/schemas/PaymentAttempt",
    "BKL-037: canje devuelve intento persistente, no orden confirmada");
  const paymentReconciliation =
    doc.paths["/api/admin/payment-events/{eventId}/reconcile"]?.post;
  assert.equal(paymentReconciliation?.["x-implementation-status"], "implemented",
    "BKL-040: la conciliación simulada ADMIN debe estar declarada");
  assert.deepEqual(paymentReconciliation.security, [{ bearerAuth: [] }],
    "BKL-040: reconciliación requiere Bearer ADMIN");
  assert.equal(paymentReconciliation.requestBody, undefined,
    "BKL-040: no admitir resultados, montos ni referencias fabricadas");
  assert.equal(paymentReconciliation.parameters?.find(p =>
    p.in === "path" && p.name === "eventId")?.required, true,
    "BKL-040: el identificador de evento es obligatorio");
  assert.equal(paymentReconciliation.responses["200"]?.content?.["application/json"]?.schema?.$ref,
    "#/components/schemas/PaymentReconciliationResult",
    "BKL-040: la conciliación devuelve evidencia, no recibo bancario");
  assert.deepEqual(doc.components.schemas.PaymentReconciliationResult?.properties?.status?.enum,
    ["RECEIVED", "CONFIRMED_SIMULATED", "DECLINED", "NEEDS_REVIEW"],
    "BKL-040: distinguir estados de confirmación simulada y conciliación");
  const paymentIncidents =
    doc.paths["/api/admin/orders/{orderId}/payment-incidents"]?.get;
  assert.equal(paymentIncidents?.["x-implementation-status"], "implemented",
    "BKL-041: debe existir consulta ADMIN de incidencias");
  assert.deepEqual(paymentIncidents?.security, [{ bearerAuth: [] }],
    "BKL-041: consultar incidencias requiere Bearer ADMIN");
  assert.equal(paymentIncidents?.requestBody, undefined,
    "BKL-041: lectura de incidencias sin cuerpo");
  assert.equal(paymentIncidents?.parameters?.find(p =>
    p.in === "path" && p.name === "orderId")?.required, true,
    "BKL-041: se requiere ID del pedido");
  assert.equal(paymentIncidents?.responses?.["200"]?.content?.["application/json"]?.schema?.items?.$ref,
    "#/components/schemas/PaymentIncident",
    "BKL-041: respuesta es lista de incidencias, no confirmación");
  assert.deepEqual(doc.components.schemas.PaymentIncident?.properties?.reason?.enum,
    ["AMBIGUOUS_AUTHORIZATION","AWAITING_VERIFIED_APPROVAL",
     "LATE_APPROVAL","EVIDENCE_MISMATCH"],
    "BKL-041: causas de riesgo consistentes con Flyway V15");
  // BKL-042: client owner-scoping + separación explícita de rutas ADMIN.
  for (const [route, schema] of [
    ["/api/orders", "OrderPage"],
    ["/api/orders/{id}", "Order"],
    ["/api/orders/{id}/history", null]
  ]) {
    const op = doc.paths[route]?.get;
    assert.equal(op?.["x-implementation-status"], "implemented",
      "BKL-042: lectura CLIENT no implementada");
    assert.deepEqual(op?.security, [{ bearerAuth: [] }],
      "BKL-042: lectura CLIENT requiere Bearer");
    if (schema) assert.equal(op?.responses?.["200"]?.content?.["application/json"]?.schema?.$ref,
      "#/components/schemas/" + schema,
      "BKL-042: CLIENT no debe recibir DTO administrativo");
    assert.ok(op?.responses?.["404"] || route === "/api/orders",
      "BKL-042: detalle ajeno debe tener 404");
  }
  const timeline = doc.paths["/api/orders/{id}/history"]?.get;
  assert.equal(timeline?.responses?.["200"]?.content?.["application/json"]?.schema?.items?.$ref,
    "#/components/schemas/OrderTimelineEntry",
    "BKL-042: historial usa DTO público sin actorId");
  const historyFields = Object.keys(doc.components.schemas.OrderTimelineEntry?.properties ?? {});
  assert.ok(!historyFields.includes("actorId") &&
    !historyFields.includes("id") && !historyFields.includes("orderId"),
    "BKL-042: historial CLIENT sin IDs globales de auditoría ni actores");
  const summaryFields = Object.keys(doc.components.schemas.OrderSummary?.properties ?? {});
  assert.ok(!summaryFields.includes("ownerUserId") &&
    !summaryFields.includes("shippingAddress"),
    "BKL-042: listado CLIENT no revela datos ajenos ni dirección");
  for (const [route, schema] of [
    ["/api/admin/orders", "AdminOrderPage"],
    ["/api/admin/orders/{id}", "AdminOrderDetail"]
  ]) {
    const op = doc.paths[route]?.get;
    assert.equal(op?.["x-implementation-status"], "implemented",
      "BKL-042: lectura ADMIN no implementada");
    assert.deepEqual(op?.security, [{ bearerAuth: [] }],
      "BKL-042: lectura ADMIN requiere Bearer");
    assert.equal(op?.responses?.["200"]?.content?.["application/json"]?.schema?.$ref,
      "#/components/schemas/" + schema,
      "BKL-042: respuesta ADMIN requiere DTO autorizado");
  }
  for (const route of ["/api/orders", "/api/admin/orders"]) {
    const params = doc.paths[route].get.parameters ?? [];
    assert.equal(params.find(p => p.name === "limit" && p.in === "query")?.schema?.maximum,
      100, "BKL-042: máximo 100 filas por página");
    assert.equal(params.find(p => p.name === "beforeId" && p.in === "query")?.schema?.minimum,
      1, "BKL-042: cursor positivo");
    assert.ok(!params.some(p => p.name === "userId" || p.name === "ownerUserId"),
      "BKL-042: identidad no puede provenir del navegador");
  }
  // BKL-052: nunca representar un registro SIMULATOR como entrega real.
  const shipment = doc.components.schemas.ShipmentRecord;
  const tracking = doc.components.schemas.ShippingTrackingView;
  assert.deepEqual(shipment.properties.fulfillmentMethod.enum,
    ["MANUAL", "SIMULATOR"], "BKL-052: métodos logísticos explícitos");
  assert.deepEqual(tracking.properties.fulfillmentMethod.enum,
    ["MANUAL", "SIMULATOR"], "BKL-052: cliente debe identificar simulación");
  assert.ok(tracking.required.includes("fulfillmentMethod"),
    "BKL-052: el origen logístico es obligatorio");
  const reasons = doc.components.schemas.ShipmentHistoryEntry.properties.reason.enum;
  assert.ok(reasons.includes("SIMULATOR_REGISTERED")
    && reasons.includes("SIMULATOR_DISPATCH")
    && reasons.includes("SIMULATOR_DELIVERY"),
    "BKL-052: la auditoría diferencia eventos de simulación");
  assert.equal(proposedCount, 0, "BKL-042: ya no quedan rutas pendientes");
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
