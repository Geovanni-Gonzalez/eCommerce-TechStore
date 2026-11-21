/**
 * BKL-019 — Smoke de navegador REAL sobre catálogo Next.js → Spring Boot.
 * Ejecuta exclusivamente en CI con los fixtures de PostgreSQL techstore_test.
 */
import assert from "node:assert/strict";
import { mkdirSync } from "node:fs";
import { chromium } from "playwright";

const WEB = "http://localhost:3000";
const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage({ viewport: { width: 1280, height: 850 } });

try {
  await page.goto(WEB + "/productos");
  await page.getByRole("heading", { name: "Explora nuestros productos" }).waitFor();
  await page.getByRole("link", { name: "Mouse inalámbrico Demo" }).waitFor({ timeout: 15000 });
  await page.getByRole("link", { name: "Teclado mecánico Demo" }).waitFor();
  assert.equal(await page.getByText("Producto oculto Demo").count(), 0,
    "Un producto inactivo no debe aparecer en el catálogo");
  await page.getByRole("img", {
    name: "Fotografía no disponible para Mouse inalámbrico Demo",
  }).waitFor();

  await page.getByRole("link", { name: "Mouse inalámbrico Demo" }).click();
  await page.getByRole("heading", { name: "Mouse inalámbrico Demo" }).waitFor();
  await page.getByText("Referencia: DEMO-MOUSE-01").waitFor();
  await page.getByText("Mouse de ejemplo para TechStore").waitFor();
  await page.getByText(/Disponible · 15 unidades/).waitFor();

  await page.goto(WEB + "/productos/99999999999");
  await page.getByRole("heading", { name: "Producto no disponible" }).waitFor();

  await page.goto(WEB + "/productos?page=incorrecta");
  await page.getByRole("heading", { name: "Página no válida" }).waitFor();
  console.log("BKL-019 E2E Chromium: catálogo API, detalle, imagen ausente y errores OK.");
} catch (error) {
  mkdirSync("artifacts/e2e", { recursive: true });
  await page.screenshot({
    path: "artifacts/e2e/failure-catalog.png", fullPage: true,
  }).catch(() => {});
  throw error;
} finally {
  await page.close();
  await browser.close();
}
