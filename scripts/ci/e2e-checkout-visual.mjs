/**
 * BKL-034 — navegador real: entrega → revisión/cotización → pedido → sandbox
 * con PostgreSQL efímero. Sin tarjeta ni dinero real.
 */
import assert from "node:assert/strict";
import {randomUUID} from "node:crypto";
import {mkdirSync} from "node:fs";
import {chromium} from "playwright";

const browser = await chromium.launch({headless:true,args:["--no-sandbox"]});
const page = await browser.newPage({viewport:{width:1280,height:850}});
const email = "bkl034-" + randomUUID() + "@example.test";
const password = "ClaveCheckoutE2E2026";
const outcome = process.env.E2E_OUTCOME;
assert.ok(outcome === "APPROVED" || outcome === "DECLINED",
  "El E2E requiere pago sandbox configurado");

try {
  await page.goto("http://localhost:3000/checkout/revision");
  await page.getByRole("heading",{name:"Inicia sesión para revisar el pedido"}).waitFor();
  await page.goto("http://localhost:3000/registro");
  await page.getByLabel("Nombre completo").fill("Cliente Checkout E2E");
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button",{name:"Registrarme"}).click();
  await page.getByRole("heading",{name:"Cuenta creada correctamente"}).waitFor({timeout:15000});
  await page.getByRole("link",{name:"Ir a iniciar sesión"}).click();
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button",{name:"Ingresar a mi cuenta"}).click();
  await page.waitForURL("**/cuenta",{timeout:15000});

  // El token de cliente vive solo en AuthProvider; navegación Next interna.
  await page.getByRole("link",{name:"Productos",exact:true}).click();
  await page.getByRole("button",{name:"Añadir Mouse inalámbrico Demo al carrito"}).click();
  await page.waitForURL("**/carrito",{timeout:15000});
  await page.getByRole("link",{name:"Preparar dirección de entrega"}).click();
  await page.waitForURL("**/checkout/entrega",{timeout:15000});
  await page.getByLabel("Provincia").selectOption("San José");
  await page.getByLabel("Cantón").fill("Central");
  await page.getByLabel("Distrito").fill("Carmen");
  await page.getByLabel("Nombre de quien recibe").fill("Cliente Checkout E2E");
  await page.getByLabel("Teléfono de contacto").fill("8888-1111");
  await page.getByLabel("Señas y referencias").fill("Casa E2E junto al parque central");
  await page.getByRole("button",{name:"Guardar y revisar dirección"}).click();
  await page.getByRole("heading",{name:"Dirección preparada"}).waitFor();
  await page.getByRole("link",{name:"Continuar a revisión del pedido"}).click();
  await page.waitForURL("**/checkout/revision",{timeout:15000});
  await page.getByRole("heading",{name:"Revisar y confirmar pedido"}).waitFor();

  await page.getByText("Impuestos estimados por el servidor").waitFor();
  await page.getByRole("button",{name:"Confirmar pedido y reservar existencias"})
    .waitFor({timeout:15000});
  assert.equal(await page.getByRole("button",{name:"Continuar a pago simulado"}).count(),0);
  await page.getByRole("button",{name:"Confirmar pedido y reservar existencias"}).click();
  const heading = page.getByRole("heading",{name:/^Pedido #[0-9]+ creado$/});
  await heading.waitFor({timeout:15000});
  const text = await heading.innerText();
  const orderId = Number(text.match(/#[0-9]+/)?.[0].slice(1));
  assert.ok(Number.isSafeInteger(orderId) && orderId > 0);
  await page.getByText("Pedido en estado PENDING con reserva de inventario.",
    {exact:false}).waitFor();

  // El paso de pago debe ser explícito y nunca pasar el Bearer en URL.
  await page.getByRole("button",{name:"Continuar a pago simulado"}).click();
  await page.waitForURL(/\/pagos\/sandbox/, {timeout:15000});
  await page.getByRole("heading",{name:"Pago simulado"}).waitFor();
  await page.getByRole("button",{name:"Ejecutar simulación"}).waitFor({timeout:15000});
  const url = new URL(page.url());
  assert.equal(url.pathname,"/pagos/sandbox");
  assert.equal(url.search,"");
  assert.equal(url.hash,"", "El sandbox debe retirar el token del fragmento");
  await page.getByRole("button",{name:"Ejecutar simulación"}).click();
  await page.getByRole("heading",{name:"Simulación completada"}).waitFor({timeout:15000});
  await page.getByRole("link",{name:"Consultar resultado verificado del pedido"}).click();
  await page.getByRole("heading",{name:"Estado de tu simulación"}).waitFor();
  await page.getByLabel("Correo electrónico").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button",{name:"Consultar estado real del backend"}).click();
  if(outcome === "DECLINED") {
    await page.getByRole("heading",{name:"Simulación rechazada"}).waitFor({timeout:15000});
  } else {
    // No existe webhook HMAC aquí: intento APPROVED no basta para confirmar pedido.
    await page.getByRole("heading",{name:"Resultado pendiente"}).waitFor({timeout:15000});
  }
  console.log("BKL-034 E2E Chromium: entrega, cotización, pedido #" +
    orderId + ", sandbox " + outcome + " y verificación segura OK.");
} catch(error) {
  mkdirSync("artifacts/e2e",{recursive:true});
  await page.screenshot({path:"artifacts/e2e/failure-checkout-visual.png",
    fullPage:true}).catch(()=>{});
  throw error;
} finally {
  await page.close();
  await browser.close();
}
