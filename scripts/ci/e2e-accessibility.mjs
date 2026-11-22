/**
 * BKL-054: regresión automatizada de accesibilidad de los flujos públicos.
 * No reemplaza auditoría manual WCAG ni pruebas con lector de pantalla.
 */
import assert from "node:assert/strict";
import {mkdirSync} from "node:fs";
import {chromium} from "playwright";

const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});
const page=await browser.newPage();
const origin="http://localhost:3000";
try {
  for (const viewport of [{width:1280,height:800},{width:390,height:844}]) {
    await page.setViewportSize(viewport);

    for (const target of [
      {path:"/productos",heading:"Explora nuestros productos"},
      {path:"/login",heading:"Iniciar sesión"},
      {path:"/registro",heading:"Crear cuenta"},
    ]) {
      await page.goto(origin+target.path);
      await page.getByRole("heading",{name:target.heading,level:1}).waitFor();
      const geometry=await page.evaluate(()=>({
        viewport:document.documentElement.clientWidth,
        content:document.documentElement.scrollWidth,
      }));
      assert.ok(geometry.content<=geometry.viewport+2,
        target.path+" produce desbordamiento horizontal con "+viewport.width+"px");

      // El enlace de salto debe ser la primera parada de Tab.
      await page.keyboard.press("Tab");
      const focus=await page.evaluate(()=>{
        const active=document.activeElement;
        if(!(active instanceof HTMLElement))return null;
        const style=getComputedStyle(active);
        return {skip:active.classList.contains("skip-link"),
          outline:parseFloat(style.outlineWidth)||0};
      });
      assert.ok(focus?.skip, "Tab inicial debe enfocar el enlace de salto");
      assert.ok(focus.outline>=2, "El foco de teclado debe ser visible");
      await page.keyboard.press("Enter");
      assert.equal(new URL(page.url()).hash,"#contenido",
        "El enlace de salto debe llevar a la región principal");
    }

    await page.goto(origin+"/productos");
    await page.getByRole("heading",{name:"Explora nuestros productos"}).waitFor();
    await page.getByRole("searchbox",{name:"Buscar por nombre o palabra clave"}).waitFor();
    await page.getByRole("combobox",{name:"Categoría"}).waitFor();
    await page.getByLabel("Marca").waitFor();
    await page.getByRole("button",{name:"Aplicar filtros"}).waitFor();
    await page.goto(origin+"/login");
    await page.getByLabel("Correo electrónico").waitFor();
    await page.getByLabel("Contraseña").waitFor();
  }
  console.log("BKL-054: foco visible, skip link, etiquetas y ancho móvil/desktop OK.");
} catch(error){
  mkdirSync("artifacts/e2e",{recursive:true});
  await page.screenshot({path:"artifacts/e2e/failure-a11y.png",fullPage:true})
    .catch(()=>{});
  throw error;
} finally {
  await page.close();
  await browser.close();
}
