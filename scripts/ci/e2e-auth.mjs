/**
 * BKL-017: Chromium → Next.js → Spring Boot → PostgreSQL efímero.
 * Registro público, sesión cliente por rol, cierre y bloqueo de ADMIN.
 */
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdirSync } from "node:fs";
import { chromium } from "playwright";

const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage({ viewport: { width: 1280, height: 850 } });
const email = `bkl017-${randomUUID()}@example.test`;
const password = "ClaveSeguraE2E2026";
try {
  await page.goto("http://localhost:3000/registro");
  await page.getByRole("heading", {name: "Crear cuenta"}).waitFor();
  await page.getByLabel("Nombre completo").fill("Cliente Prueba E2E");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", {name: "Registrarme"}).click();
  await page.getByRole("heading", {name: "Cuenta creada correctamente"}).waitFor({ timeout: 15000 });
  await page.getByRole("link", {name: "Ir a iniciar sesión"}).click();
  await page.getByRole("heading", {name: "Iniciar sesión"}).waitFor();
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", {name: "Ingresar a mi cuenta"}).click();
  await page.waitForURL("**/cuenta", { timeout: 15000 });
  await page.getByText("Bienvenido, Cliente Prueba E2E.").waitFor();
  await page.getByText(email).waitFor();
  await page.getByText("Cliente", { exact: true }).waitFor();
  assert.equal(await page.getByText("Administración", { exact: true }).count(), 0);

  await page.getByRole("button", {name: "Cerrar sesión"}).click();
  await page.waitForURL("**/login", { timeout: 15000 });
  await page.goto("http://localhost:3000/cuenta");
  await page.getByRole("heading", {name: "Acceso privado"}).waitFor();
  assert.equal(await page.getByText(email).count(), 0);

  await page.goto("http://localhost:3000/admin");
  await page.getByRole("heading", {name: "Acceso restringido"}).waitFor();
  assert.equal(await page.getByText("Permiso ADMIN confirmado por la API.").count(), 0);
  console.log("BKL-017 E2E Chromium: registro/login CLIENT, rol, logout y ADMIN restringido OK.");
} catch (error) {
  mkdirSync("artifacts/e2e", { recursive: true });
  await page.screenshot({ path: "artifacts/e2e/failure-auth.png", fullPage: true })
    .catch(() => {});
  throw error;
} finally {
  await page.close();
  await browser.close();
}
