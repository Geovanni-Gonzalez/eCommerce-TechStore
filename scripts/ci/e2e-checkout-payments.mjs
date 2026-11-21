/**
 * BKL-044 — Chromium real + Next.js + Spring Boot + PostgreSQL efímero.
 * No hay pagos reales. El checkout visual /carrito sigue pendiente de
 * BKL-019/BKL-031: el carrito/pedido se prepara vía API HTTP real.
 *
 * Ejecutar desde repo raíz con 8080 (SANDBOX opt-in + webhook HMAC), 3000
 * y E2E_DB_CONTAINER apuntando al contenedor efímero techstore_test.
 * E2E_OUTCOME=APPROVED o DECLINED configura el mismo resultado en backend.
 */
import assert from "node:assert/strict";
import { createHmac, randomUUID } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdirSync } from "node:fs";
import { chromium, request as playwrightRequest } from "playwright";

const API = "http://localhost:8080";
const WEB = "http://localhost:3000";
const DB = process.env.E2E_DB_CONTAINER;
const OUTCOME = process.env.E2E_OUTCOME;
const SECRET = process.env.E2E_WEBHOOK_SECRET;
const PASSWORD = "ClaveTemporalE2E2026";
const ADDRESS = {
  province: "San José", canton: "Central", district: "Carmen",
  details: "Casa de verificación E2E junto al parque",
  recipientName: "Cliente verificado E2E", phone: "8888-1111",
};

assert.ok(DB && /^[a-f0-9]{12,64}$/.test(DB), "Solo contenedor efímero PostgreSQL");
assert.ok(["APPROVED", "DECLINED"].includes(OUTCOME), "E2E_OUTCOME requerido");
assert.ok(SECRET && SECRET.length >= 32, "Secreto HMAC de pruebas requerido");

function scalar(sql) {
  // Solo se interpolan IDs numéricos validados y SQL literal de los tests.
  const result = execFileSync("docker",
    ["exec", DB, "psql", "-U", "techstore", "-d", "techstore_test",
      "-v", "ON_ERROR_STOP=1", "-At", "-c", sql],
    { encoding: "utf8", timeout: 15000 });
  return result.trim();
}
function dbInt(sql) {
  const result = Number(scalar(sql));
  assert.ok(Number.isSafeInteger(result), "Resultado SQL inválido");
  return result;
}

async function checkResponse(response, expected, label) {
  assert.equal(response.status(), expected,
    label + " (HTTP " + response.status() + ", esperado " + expected + ")");
  if (expected === 204) return null;
  const value = await response.json();
  assert.ok(!JSON.stringify(value).includes(PASSWORD),
    "La API devolvió una contraseña");
  return value;
}
async function get(ctx, path, token, expected = 200) {
  const response = await ctx.get(API + path, {
    headers: token ? { Authorization: "Bearer " + token } : {},
  });
  return checkResponse(response, expected, "GET " + path);
}
async function post(ctx, path, data, token, expected = 200, headers = {}) {
  const opts = { headers: { ...headers } };
  if (token) opts.headers.Authorization = "Bearer " + token;
  if (data !== undefined) opts.data = data;
  const response = await ctx.post(API + path, opts);
  return checkResponse(response, expected, "POST " + path);
}

async function createClient(ctx, suffix) {
  const email = "e2e." + OUTCOME.toLowerCase() + "." +
    suffix + "." + randomUUID() + "@techstore.test";
  const created = await post(ctx, "/api/auth/register", {
    displayName: "Cliente E2E", email, password: PASSWORD,
  }, null, 201);
  const logged = await post(ctx, "/api/auth/login", {
    email, password: PASSWORD,
  });
  assert.equal(logged.user.id, created.id);
  assert.equal(logged.user.role, "CLIENT");
  assert.equal(logged.tokenType, "Bearer");
  assert.ok(/^[A-Za-z0-9_-]{43}$/.test(logged.accessToken));
  return { email, token: logged.accessToken, id: created.id };
}

async function loginBrowser(page, email) {
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(PASSWORD);
  await page.getByRole("button", { name: /Consultar estado real del backend/ }).click();
}

async function verifyOwnerOrderHistory(page, orderId, order, customer) {
  await page.goto(WEB + "/pedidos");
  await page.getByLabel("Correo electrónico").fill(customer.email);
  await page.getByLabel("Contraseña").fill(PASSWORD);
  await page.getByRole("button", { name: "Consultar mis pedidos" }).click();
  await page.getByRole("button", {
    name: "Consultar detalle del pedido " + orderId,
  }).waitFor({ timeout: 15000 });
  await page.getByRole("button", {
    name: "Consultar detalle del pedido " + orderId,
  }).click();
  await page.getByRole("heading", { name: "Pedido #" + orderId }).waitFor();
  await page.getByRole("heading", { name: "Productos del pedido" }).waitFor();
  await page.getByText("Mouse inalámbrico Demo").last().waitFor();
  await page.getByText(ADDRESS.details).waitFor();
  await page.getByRole("heading", { name: "Historial de estados" }).waitFor();
  assert.ok(order.totals.totalCrc > 0);
  if (OUTCOME === "APPROVED") {
    await page.getByText("Confirmación de simulación verificada").waitFor();
  } else {
    await page.getByText("Pedido creado").waitFor();
    assert.equal(await page.getByText("Confirmación de simulación verificada").count(), 0);
  }
  await page.getByRole("button", { name: "Cerrar sesión" }).click();
  await page.getByRole("heading", { name: "Consulta solo tus compras" }).waitFor();
  assert.equal(await page.getByText(ADDRESS.details).count(), 0,
    "PII visible después de cerrar sesión");
}

async function run() {
  assert.equal(scalar("SELECT current_database()"), "techstore_test",
    "No ejecutar E2E contra bases reales");
  const ctx = await playwrightRequest.newContext({
    timeout: 15000, extraHTTPHeaders: { Accept: "application/json" },
  });
  const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  const page = await browser.newPage({ viewport: { width: 1280, height: 850 } });
  let orderId = null;
  try {
    const owner = await createClient(ctx, "owner");
    const outsider = await createClient(ctx, "outsider");

    const catalog = await get(ctx, "/api/products");
    const product = catalog.items.find(item => item.sku === "DEMO-MOUSE-01");
    assert.ok(product && product.stock >= 2, "Fixture DEMO-MOUSE-01 ausente");
    const initialStock = dbInt(
      "SELECT stock FROM products WHERE id=" + product.id);
    assert.equal(initialStock, product.stock);
    await post(ctx, "/api/cart/items", {
      productId: product.id, quantity: 2,
    }, owner.token);
    const quote = await get(ctx, "/api/checkout/quote", owner.token);
    assert.equal(quote.totalItems, 2);
    assert.equal(quote.totals.subtotalCrc, product.priceCrc * 2);

    // Checkout HTTP real -> tablas orders, order_items, stock_reservations.
    const order = await post(ctx, "/api/orders", {
      shippingAddress: ADDRESS,
    }, owner.token, 201);
    orderId = order.id;
    assert.ok(Number.isSafeInteger(orderId) && orderId > 0);
    assert.equal(order.status, "PENDING");
    assert.equal(order.paymentMode, "SIMULATED");
    assert.equal(order.totals.totalCrc, quote.totals.totalCrc);
    assert.equal(dbInt("SELECT COUNT(*) FROM orders WHERE id=" + orderId), 1);
    assert.equal(dbInt("SELECT COUNT(*) FROM order_items WHERE order_id=" + orderId +
      " AND product_id=" + product.id + " AND quantity=2"), 1);
    assert.equal(scalar("SELECT status FROM stock_reservations WHERE order_id=" +
      orderId), "RESERVED");
    assert.equal(dbInt("SELECT stock FROM products WHERE id=" + product.id),
      initialStock - 2, "El stock debe reservarse exactamente una vez");

    // Propietario/IDOR: BKL-042 y BKL-036 jamás revelan pedido ajeno.
    const denied = await get(ctx, "/api/orders/" + orderId, outsider.token, 404);
    const missing = await get(ctx, "/api/orders/99999999999", outsider.token, 404);
    assert.equal(denied.code, missing.code);
    await get(ctx, "/api/orders/" + orderId + "/history", outsider.token, 404);
    await get(ctx, "/api/orders/" + orderId, null, 401);

    const startPath = "/api/orders/" + orderId + "/payment-sessions";
    const key = "e2e-" + OUTCOME.toLowerCase() + "-" +
      randomUUID().replaceAll("-", "");
    await post(ctx, startPath, undefined, outsider.token, 404,
      { "Idempotency-Key": key });
    const first = await post(ctx, startPath, undefined, owner.token, 201,
      { "Idempotency-Key": key });
    assert.equal(first.orderId, orderId);
    assert.equal(first.totalCrc, order.totals.totalCrc);
    assert.equal(first.currency, "CRC");
    assert.match(first.redirectUrl,
      /^http:\/\/localhost:3000\/pagos\/sandbox#session=[A-Za-z0-9_-]{43}$/);

    // Mismo Idempotency-Key rota token sin crear otro intento.
    const rotated = await post(ctx, startPath, undefined, owner.token, 200,
      { "Idempotency-Key": key });
    assert.equal(rotated.attemptId, first.attemptId);
    assert.notEqual(rotated.redirectUrl, first.redirectUrl);
    const oldToken = first.redirectUrl.split("#session=")[1];
    await post(ctx, "/api/payments/sandbox/sessions/preview",
      { token: oldToken }, null, 404);
    assert.equal(dbInt("SELECT COUNT(*) FROM payment_attempts WHERE order_id=" +
      orderId), 1);

    // Navegación REAL de Chromium a Next.js; JavaScript de la página
    // preview -> complete -> página de resultados.
    await page.goto(rotated.redirectUrl);
    await page.getByRole("heading", { name: "Pago simulado" }).waitFor();
    await page.getByRole("button", { name: "Ejecutar simulación" }).waitFor();
    const fragment = await page.evaluate(() => window.location.hash);
    assert.equal(fragment, "", "El token sandbox no debe conservarse en la URL");
    await page.getByRole("button", { name: "Ejecutar simulación" }).click();
    await page.getByRole("heading", { name: "Simulación completada" })
      .waitFor({ timeout: 15000 });
    await page.getByText("Estado del intento:").waitFor();
    const expectedAttemptStatus = OUTCOME === "APPROVED"
      ? "SIMULATED_APPROVED" : "SIMULATED_DECLINED";
    const persistedAttempt = await get(ctx,
      "/api/orders/" + orderId + "/payment-attempts/" + rotated.attemptId,
      owner.token);
    assert.equal(persistedAttempt.status, expectedAttemptStatus);
    assert.ok(persistedAttempt.providerReference?.startsWith("SBX-"));
    await get(ctx,
      "/api/orders/" + orderId + "/payment-attempts/" + rotated.attemptId,
      outsider.token, 404);

    const currentToken = rotated.redirectUrl.split("#session=")[1];
    const repeatedCompletion = await post(ctx,
      "/api/payments/sandbox/sessions/complete", { token: currentToken },
      null, 404);
    assert.equal(repeatedCompletion.code, "SANDBOX_SESSION_NOT_FOUND");
    assert.equal(dbInt("SELECT COUNT(*) FROM payment_attempts WHERE order_id=" +
      orderId), 1);
    assert.equal(dbInt("SELECT stock FROM products WHERE id=" + product.id),
      initialStock - 2, "No puede haber doble descuento al repetir la sesión");

    await page.getByRole("link", {
      name: "Consultar resultado verificado del pedido",
    }).click();
    await page.getByRole("heading", { name: "Estado de tu simulación" }).waitFor();
    await loginBrowser(page, owner.email);

    if (OUTCOME === "APPROVED") {
      // Intento APROBADO no equivale a pedido CONFIRMED. Sin HMAC, PENDING.
      await page.getByRole("heading", { name: "Resultado pendiente" })
        .waitFor({ timeout: 15000 });
      assert.equal(scalar("SELECT status FROM orders WHERE id=" + orderId),
        "PENDING");
      assert.equal(scalar("SELECT status FROM stock_reservations WHERE order_id=" +
        orderId), "RESERVED");

      const event = {
        eventId: "evt-e2e-" + randomUUID().replaceAll("-", "").slice(0,16),
        providerReference: persistedAttempt.providerReference,
        orderId,
        totalCrc: order.totals.totalCrc,
        outcome: "APPROVED",
      };
      const rawBody = JSON.stringify(event);
      const timestamp = String(Math.floor(Date.now() / 1000));
      const signature = "v1=" + createHmac("sha256", SECRET)
        .update(timestamp + "." + rawBody).digest("hex");

      // Notificación sin firma debe fallar y no afectar la base.
      await checkResponse(await ctx.post(API + "/api/payments/webhooks/sandbox", {
        data: rawBody, headers: { "Content-Type": "application/json" },
      }), 401, "Webhook sin HMAC");
      assert.equal(scalar("SELECT status FROM orders WHERE id=" + orderId),
        "PENDING");

      const signedHeaders = {
        "Content-Type": "application/json",
        "X-TechStore-Webhook-Timestamp": timestamp,
        "X-TechStore-Webhook-Signature": signature,
      };
      const receipt = await checkResponse(
        await ctx.post(API + "/api/payments/webhooks/sandbox", {
          data: rawBody, headers: signedHeaders,
        }), 202, "Webhook firmado");
      const duplicate = await checkResponse(
        await ctx.post(API + "/api/payments/webhooks/sandbox", {
          data: rawBody, headers: signedHeaders,
        }), 200, "Webhook duplicado");
      assert.equal(receipt.eventId, duplicate.eventId);
      assert.equal(scalar("SELECT status FROM orders WHERE id=" + orderId),
        "CONFIRMED");
      assert.equal(scalar("SELECT status FROM stock_reservations WHERE order_id=" +
        orderId), "CONSUMED");
      assert.equal(dbInt("SELECT COUNT(*) FROM order_status_history WHERE order_id=" +
        orderId + " AND reason='SANDBOX_VERIFIED'"), 1);
      assert.equal(dbInt("SELECT COUNT(*) FROM payment_webhook_events WHERE " +
        "payment_attempt_id=" + rotated.attemptId), 1);
      assert.equal(dbInt("SELECT stock FROM products WHERE id=" + product.id),
        initialStock - 2, "Una conciliación duplicada no descuenta stock dos veces");
      await page.getByRole("button", { name: "Comprobar de nuevo" }).click();
      await page.getByRole("heading", { name: "Simulación aprobada" })
        .waitFor({ timeout: 15000 });
    } else {
      await page.getByRole("heading", { name: "Simulación rechazada" })
        .waitFor({ timeout: 15000 });
      assert.equal(scalar("SELECT status FROM orders WHERE id=" + orderId),
        "PENDING");
      assert.equal(scalar("SELECT status FROM stock_reservations WHERE order_id=" +
        orderId), "RESERVED");
      assert.equal(dbInt("SELECT COUNT(*) FROM order_status_history WHERE order_id=" +
        orderId + " AND reason='SANDBOX_VERIFIED'"), 0);
    }

    // La pantalla histórica también debe consultar HTTP privado y ver DB real.
    await verifyOwnerOrderHistory(page, orderId, order, owner);

    // Ataque horizontal desde navegador: un cliente distinto no ve éxito.
    await page.goto(WEB + "/pagos/resultado/aprobado?orderId=" +
      orderId + "&attemptId=" + rotated.attemptId);
    await page.getByRole("heading", { name: "Estado de tu simulación" }).waitFor();
    await loginBrowser(page, outsider.email);
    await page.getByRole("heading", { name: "No se pudo verificar" })
      .waitFor({ timeout: 15000 });
    assert.equal(await page.getByRole("heading", {
      name: "Simulación aprobada",
    }).count(), 0);

    // Sin más intentos/ventas duplicados. Logout del token del setup.
    assert.equal(dbInt("SELECT COUNT(*) FROM payment_attempts WHERE order_id=" +
      orderId), 1);
    await post(ctx, "/api/auth/logout", undefined, owner.token, 204);
    await post(ctx, "/api/auth/logout", undefined, outsider.token, 204);
    console.log("BKL-044 E2E " + OUTCOME +
      ": navegador Chromium, checkout API, duplicados, estados, IDOR y SQL OK.");
  } catch (error) {
    mkdirSync("artifacts/e2e", { recursive: true });
    await page.screenshot({
      path: "artifacts/e2e/failure-" + OUTCOME.toLowerCase() + ".png",
      fullPage: true,
    }).catch(() => {});
    console.error("BKL-044 E2E falló en escenario " + OUTCOME +
      (orderId !== null ? " pedido #" + orderId : ""));
    throw error;
  } finally {
    await page.close();
    await browser.close();
    await ctx.dispose();
  }
}
await run();
