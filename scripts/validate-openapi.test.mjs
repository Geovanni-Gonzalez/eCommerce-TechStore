import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { validateContract } from "./validate-openapi.mjs";

const original = JSON.parse(readFileSync("docs/api/openapi.json", "utf8"));
const clone = () => structuredClone(original);

test("el contrato actual es consistente", () => {
  const result = validateContract(original);
  assert.equal(result.operationCount, 32);
  assert.equal(result.proposedCount, 0);
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

test("cancelación requiere Bearer y se declara implementada", () => {
  const noAuth = clone();
  delete noAuth.paths["/api/orders/{id}/cancel"].post.security;
  assert.throws(() => validateContract(noAuth), /Cancelar pedido exige Bearer/);
  const planned = clone();
  planned.paths["/api/orders/{id}/cancel"].post["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(planned),
    /La cancelación de reservas debe estar implementada/);
});

test("BKL-045 exige Bearer y DTO de historial para ADMIN", () => {
  const noSecurity = clone();
  delete noSecurity.paths["/api/admin/orders/{id}/history"].get.security;
  assert.throws(() => validateContract(noSecurity),
    /BKL-045: historial administrativo exige Bearer/);
  const noAuthChange = clone();
  delete noAuthChange.paths["/api/admin/orders/{id}/status"].patch.security;
  assert.throws(() => validateContract(noAuthChange),
    /BKL-045: cambio administrativo exige Bearer/);
  const planned = clone();
  planned.paths["/api/admin/orders/{id}/status"].patch["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(planned),
    /BKL-045: transición administrativa debe estar implementada/);
});

test("BKL-036 prohíbe endpoint de pagos sin Bearer o sin clave de idempotencia", () => {
  const noAuth = clone();
  delete noAuth.paths["/api/orders/{orderId}/payment-attempts"].post.security;
  assert.throws(() => validateContract(noAuth), /registrar intento requiere Bearer/);
  const noKey = clone();
  noKey.paths["/api/orders/{orderId}/payment-attempts"].post.parameters =
    noKey.paths["/api/orders/{orderId}/payment-attempts"].post.parameters
      .filter(p => p.name !== "Idempotency-Key");
  assert.throws(() => validateContract(noKey), /Idempotency-Key es obligatorio/);
  const withPrice = clone();
  withPrice.paths["/api/orders/{orderId}/payment-attempts"].post.requestBody = {
    content: {"application/json": {schema: {type: "object"}}}
  };
  assert.throws(() => validateContract(withPrice),
    /no aceptar montos, credenciales o body de pagos/);
  const planned = clone();
  planned.paths["/api/orders/{orderId}/payment-attempts/{attemptId}"]
    .get["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(planned),
    /consulta de intento debe estar implementada/);
});

test("BKL-038 rechaza contrato sin firma HMAC, timestamp o respuesta de replay", () => {
  const unsigned = clone();
  delete unsigned.paths["/api/payments/webhooks/sandbox"].post.security;
  assert.throws(() => validateContract(unsigned), /autenticación HMAC/);
  const missingTime = clone();
  missingTime.paths["/api/payments/webhooks/sandbox"].post.parameters = [];
  assert.throws(() => validateContract(missingTime), /timestamp firmado requerido/);
  const missingReplay = clone();
  delete missingReplay.paths["/api/payments/webhooks/sandbox"].post.responses["200"];
  assert.throws(() => validateContract(missingReplay), /contrato exige replay/);
  const planned = clone();
  planned.paths["/api/payments/webhooks/sandbox"].post["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(planned),
    /webhook debe figurar implementado de forma condicional/);
});

test("BKL-037 requiere Bearer e idempotencia, sin body de montos ni returnUrl", () => {
  const noAuth = clone();
  delete noAuth.paths["/api/orders/{orderId}/payment-sessions"].post.security;
  assert.throws(() => validateContract(noAuth), /solo CLIENT autenticado inicia pago/);
  const noKey = clone();
  noKey.paths["/api/orders/{orderId}/payment-sessions"].post.parameters =
    noKey.paths["/api/orders/{orderId}/payment-sessions"].post.parameters
      .filter(p => p.name !== "Idempotency-Key");
  assert.throws(() => validateContract(noKey), /inicio exige Idempotency-Key/);
  const withBody = clone();
  withBody.paths["/api/orders/{orderId}/payment-sessions"].post.requestBody =
    {content: {"application/json": {schema: {type:"object"}}}};
  assert.throws(() => validateContract(withBody), /rechazar monto, moneda, tarjeta y returnUrl/);
  const noToken = clone();
  noToken.paths["/api/payments/sandbox/sessions/preview"].post.requestBody =
    {content: {"application/json": {schema: {type:"object"}}}};
  assert.throws(() => validateContract(noToken), /solo se admite token efímero/);
});

test("BKL-040 impide conciliación administrativa sin Bearer o con montos del cliente", () => {
  const anonymous = clone();
  delete anonymous.paths["/api/admin/payment-events/{eventId}/reconcile"].post.security;
  assert.throws(() => validateContract(anonymous),
    /reconciliación requiere Bearer ADMIN/);
  const body = clone();
  body.paths["/api/admin/payment-events/{eventId}/reconcile"].post.requestBody =
    {content: {"application/json": {schema: {type:"object"}}}};
  assert.throws(() => validateContract(body),
    /no admitir resultados, montos ni referencias fabricadas/);
  const unverified = clone();
  unverified.paths["/api/admin/payment-events/{eventId}/reconcile"]
    .post["x-implementation-status"] = "planned";
  assert.throws(() => validateContract(unverified),
    /conciliación simulada ADMIN debe estar declarada/);
});

test("BKL-041 obliga auth ADMIN y respuesta solo con evidencias de incidencias", () => {
  const unprotected = clone();
  delete unprotected.paths["/api/admin/orders/{orderId}/payment-incidents"].get.security;
  assert.throws(() => validateContract(unprotected),
    /consultar incidencias requiere Bearer ADMIN/);
  const forged = clone();
  forged.paths["/api/admin/orders/{orderId}/payment-incidents"].get.requestBody =
    {content: {"application/json": {schema: {type:"object"}}}};
  assert.throws(() => validateContract(forged),
    /lectura de incidencias sin cuerpo/);
  const tampered = clone();
  tampered.components.schemas.PaymentIncident.properties.reason.enum.pop();
  assert.throws(() => validateContract(tampered),
    /causas de riesgo consistentes con Flyway V15/);
});

test("BKL-042 exige Bearer en lecturas del CLIENT y ADMIN", () => {
  for (const route of ["/api/orders", "/api/orders/{id}",
    "/api/orders/{id}/history", "/api/admin/orders", "/api/admin/orders/{id}"]) {
    const copy = clone();
    delete copy.paths[route].get.security;
    assert.throws(() => validateContract(copy),
      /BKL-042: lectura CLIENT requiere Bearer|BKL-042: lectura ADMIN requiere Bearer/);
  }
});

test("BKL-042 evita exponer actorId, ownerUserId o PII en DTO CLIENT", () => {
  const adminDto = clone();
  adminDto.paths["/api/orders"].get.responses["200"]
    .content["application/json"].schema.$ref = "#/components/schemas/AdminOrderPage";
  assert.throws(() => validateContract(adminDto),
    /BKL-042: CLIENT no debe recibir DTO administrativo/);
  const actorLeak = clone();
  actorLeak.components.schemas.OrderTimelineEntry.properties.actorId =
    {type:"integer",format:"int64"};
  assert.throws(() => validateContract(actorLeak),
    /BKL-042: historial CLIENT sin IDs/);
  const addressLeak = clone();
  addressLeak.components.schemas.OrderSummary.properties.shippingAddress =
    {$ref:"#/components/schemas/ShippingAddress"};
  assert.throws(() => validateContract(addressLeak),
    /BKL-042: listado CLIENT no revela datos ajenos/);
});

test("BKL-042 impone paginación acotada sin propietario elegido", () => {
  const page = clone();
  page.paths["/api/orders"].get.parameters.find(p => p.name === "limit")
    .schema.maximum = 1000;
  assert.throws(() => validateContract(page), /BKL-042: máximo 100 filas/);
  const spoof = clone();
  spoof.paths["/api/orders"].get.parameters.push(
    {in:"query", name:"userId", schema:{type:"integer"}});
  assert.throws(() => validateContract(spoof),
    /BKL-042: identidad no puede provenir del navegador/);
});
