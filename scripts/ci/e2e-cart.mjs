/**
 * BKL-029 — Compra en interfaz (solo carrito):
 * Chromium -> Next.js -> Spring Boot -> PostgreSQL test.
 * Debe ejecutarse solo en E2E con base aislada y catálogo de prueba precargado.
 */
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdirSync } from "node:fs";
import { chromium } from "playwright";

const browser = await chromium.launch({headless:true,args:["--no-sandbox"]});
const page = await browser.newPage({viewport:{width:1280,height:850}});
const email = `bkl029-${randomUUID()}@example.test`;
const password = "ClaveCarrito2026E2E";
try {
  // Sin identidad, el carrito no debe revelar líneas de otros usuarios.
  await page.goto("http://localhost:3000/carrito");
  await page.getByRole("heading", {name:"Inicia sesión para consultar tu carrito"}).waitFor();
  await page.goto("http://localhost:3000/registro");
  await page.getByLabel("Nombre completo").fill("Cliente Carrito E2E");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", {name:"Registrarme"}).click();
  await page.getByRole("heading", {name:"Cuenta creada correctamente"}).waitFor({timeout:15000});
  await page.getByRole("link", {name:"Ir a iniciar sesión"}).click();
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", {name:"Ingresar a mi cuenta"}).click();
  await page.waitForURL("**/cuenta",{timeout:15000});

  // La sesión BKL-017 está solo en memoria: navegar mediante Next Link,
  // no con page.goto (que recarga y crea un AuthProvider nuevo).
  await page.getByRole("link", {name:"Productos", exact:true}).click();
  await page.getByRole("link", {name:"Mouse inalámbrico Demo"}).click();
  await page.getByRole("button", {name:"Añadir Mouse inalámbrico Demo al carrito"}).click();
  await page.waitForURL("**/carrito",{timeout:15000});
  await page.getByRole("heading", {name:"Mi carrito"}).waitFor();
  await page.getByRole("link", {name:"Mouse inalámbrico Demo"}).waitFor();
  await page.getByText("Productos seleccionados (1)").waitFor();
  await page.getByText("Subtotal estimado").waitFor();

  const quantity = page.getByRole("spinbutton", {name:"Cantidad de Mouse inalámbrico Demo"});
  await quantity.fill("2");
  await page.getByRole("button", {name:"Actualizar Mouse inalámbrico Demo"}).click();
  await page.getByText("Cantidad actualizada correctamente.").waitFor();
  assert.equal(await quantity.inputValue(),"2");
  await page.getByText("Productos seleccionados (2)").waitFor();

  // El logout revoca el Bearer; los datos deben permanecer en BD por cuenta.
  await page.getByRole("link", {name:"Mi cuenta"}).click();
  await page.getByRole("button", {name:"Cerrar sesión"}).click();
  await page.waitForURL("**/login",{timeout:15000});
  await page.goto("http://localhost:3000/carrito");
  await page.getByRole("heading",{name:"Inicia sesión para consultar tu carrito"}).waitFor();
  assert.equal(await page.getByText("Productos seleccionados (2)").count(),0);
  await page.goto("http://localhost:3000/login");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button",{name:"Ingresar a mi cuenta"}).click();
  await page.waitForURL("**/cuenta",{timeout:15000});
  await page.getByRole("link", {name:"Carrito", exact:true}).click();
  await page.getByText("Productos seleccionados (2)").waitFor();
  assert.equal(await page.getByRole("spinbutton",{name:"Cantidad de Mouse inalámbrico Demo"}).inputValue(),"2");

  await page.getByRole("button",{name:"Quitar Mouse inalámbrico Demo"}).click();
  await page.getByRole("heading",{name:"Tu carrito está vacío"}).waitFor();

  await page.getByRole("link", {name:"Productos", exact:true}).click();
  await page.getByRole("button",{name:"Añadir Teclado mecánico Demo al carrito"}).click();
  await page.waitForURL("**/carrito",{timeout:15000});
  await page.getByRole("button",{name:"Vaciar carrito"}).click();
  await page.getByRole("button",{name:"Confirmar vaciado"}).click();
  await page.getByRole("heading",{name:"Tu carrito está vacío"}).waitFor();

  console.log("BKL-029 E2E Chromium: agregar, editar, persistir tras logout/login, quitar y vaciar OK.");
} catch (error) {
  mkdirSync("artifacts/e2e",{recursive:true});
  await page.screenshot({path:"artifacts/e2e/failure-cart.png",fullPage:true}).catch(()=>{});
  throw error;
} finally {
  await page.close();
  await browser.close();
}
