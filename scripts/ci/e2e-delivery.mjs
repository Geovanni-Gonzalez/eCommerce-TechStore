/**
 * BKL-031 — Chromium/Next.js/PostgreSQL real.
 * La dirección solo queda en memoria; nunca se crea pedido ni se cobra.
 */
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdirSync } from "node:fs";
import { chromium } from "playwright";

const browser = await chromium.launch({headless:true,args:["--no-sandbox"]});
const page = await browser.newPage({viewport:{width:1280,height:850}});
const email = "bkl031-" + randomUUID() + "@example.test";
const password = "ClaveEntregaE2E2026";
try {
  await page.goto("http://localhost:3000/checkout/entrega");
  await page.getByRole("heading",{name:"Inicia sesión para indicar tu dirección"}).waitFor();
  assert.equal(await page.getByLabel("Teléfono de contacto").count(),0);

  await page.goto("http://localhost:3000/registro");
  await page.getByLabel("Nombre completo").fill("Cliente Entrega E2E");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button",{name:"Registrarme"}).click();
  await page.getByRole("heading",{name:"Cuenta creada correctamente"}).waitFor({timeout:15000});
  await page.getByRole("link",{name:"Ir a iniciar sesión"}).click();
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button",{name:"Ingresar a mi cuenta"}).click();
  await page.waitForURL("**/cuenta",{timeout:15000});

  // Navegación cliente Next.js: un page.goto perdería el Bearer en memoria.
  await page.getByRole("link",{name:"Productos",exact:true}).click();
  await page.getByRole("button",{name:"Añadir Mouse inalámbrico Demo al carrito"}).click();
  await page.waitForURL("**/carrito",{timeout:15000});
  await page.getByRole("link",{name:"Preparar dirección de entrega"}).click();
  await page.waitForURL("**/checkout/entrega",{timeout:15000});
  await page.getByRole("heading",{name:"Dirección de entrega"}).waitFor();

  await page.getByRole("button",{name:"Guardar y revisar dirección"}).click();
  await page.getByRole("alert").first().waitFor();
  assert.equal(await page.locator("[aria-invalid=true]").count(),6);

  await page.getByLabel("Provincia").selectOption("San José");
  await page.getByLabel("Cantón").fill("Central");
  await page.getByLabel("Distrito").fill("Carmen");
  await page.getByLabel("Nombre de quien recibe").fill("Cliente Entrega E2E");
  await page.getByLabel("Teléfono de contacto").fill("8888-1111");
  await page.getByLabel("Señas y referencias").fill("Frente a la plaza central, casa azul");

  await page.getByRole("button",{name:"Guardar y revisar dirección"}).click();
  await page.getByRole("heading",{name:"Dirección preparada"}).waitFor();
  assert.match(await page.getByRole("status").innerText(), /Aún no se ha creado ningún pedido/);
  await page.getByText("8888-1111").waitFor();

  await page.getByRole("link",{name:"Volver al carrito"}).click();
  await page.getByRole("link",{name:"Preparar dirección de entrega"}).click();
  await page.getByRole("heading",{name:"Dirección preparada"}).waitFor();
  await page.getByRole("button",{name:"Editar dirección"}).click();
  assert.equal(await page.getByLabel("Distrito").inputValue(),"Carmen");
  await page.getByLabel("Distrito").fill("Merced");
  await page.getByRole("button",{name:"Guardar y revisar dirección"}).click();
  await page.getByRole("heading",{name:"Dirección preparada"}).waitFor();
  await page.getByText("Merced").waitFor();

  await page.getByRole("link",{name:"Mi cuenta"}).click();
  await page.getByRole("button",{name:"Cerrar sesión"}).click();
  await page.waitForURL("**/login",{timeout:15000});
  await page.goto("http://localhost:3000/checkout/entrega");
  await page.getByRole("heading",{name:"Inicia sesión para indicar tu dirección"}).waitFor();
  assert.equal(await page.getByText("8888-1111").count(),0);

  console.log("BKL-031 E2E Chromium: validación, revisión, edición, sesión y privacidad OK.");
} catch (error) {
  mkdirSync("artifacts/e2e",{recursive:true});
  await page.screenshot({path:"artifacts/e2e/failure-delivery.png",fullPage:true}).catch(()=>{});
  throw error;
} finally {
  await page.close();
  await browser.close();
}
