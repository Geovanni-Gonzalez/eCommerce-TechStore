import test from "node:test";
import assert from "node:assert/strict";
import {
  parseProduct, parseCatalogPage, parseCatalogPageQuery, parseProductId,
  safeImageUrl, formatCatalogCrc,
} from "./catalog.ts";

const mouse = {
  id: 5, sku: "DEMO-MOUSE-01", name: "Mouse inalámbrico Demo",
  description: "Mouse de ejemplo", category: null, priceCrc: 12990.5,
  stock: 15, active: true,
};
const page = (items = [mouse], index = 0) => ({
  items, page: index, size: 12,
  totalElements: items.length, totalPages: items.length > 0 ? 1 : 0,
});

test("BKL-019 acepta DTO real: categoría e imagen opcionales", () => {
  const p = parseProduct(mouse, 5);
  assert.equal(p.id, 5);
  assert.equal(p.priceCrc, 12990.5);
  assert.equal(p.imageUrl, null);
  assert.equal(p.category, null);
  assert.equal(p.stock, 15);
});

test("BKL-019 acepta URL HTTPS y categoría completa", () => {
  const p = parseProduct({
    ...mouse, category: {id: 2, name: "Accesorios"},
    imageUrl: "https://cdn.example.org/mouse.png",
  });
  assert.equal(p.category.name, "Accesorios");
  assert.equal(p.imageUrl, "https://cdn.example.org/mouse.png");
});

test("BKL-019 evita imagen insegura (javascript, data, http remoto y credenciales)", () => {
  for (const url of ["javascript:alert(1)", "data:image/svg+xml,<svg/>",
    "http://example.org/p.png", "https://a:b@example.org/i.png", "/local/file.png"]) {
    assert.equal(safeImageUrl(url), null);
  }
  assert.equal(safeImageUrl("http://localhost:3000/demo.png"),
    "http://localhost:3000/demo.png");
});

test("BKL-019 no presenta productos inactivos, precios falsos o stock negativo", () => {
  for (const value of [
    {...mouse, active:false}, {...mouse, stock:-1}, {...mouse, stock:1.4},
    {...mouse, priceCrc:-2}, {...mouse, priceCrc:NaN},
    {...mouse, id:"5"}, {...mouse, name:""},
  ]) assert.throws(() => parseProduct(value));
  assert.throws(() => parseProduct(mouse, 6));
});

test("BKL-019 valida una página vacía y el índice recibido", () => {
  assert.deepEqual(parseCatalogPage(page([],0), 0).items, []);
  assert.equal(parseCatalogPage(page([mouse],0),0).totalElements,1);
  assert.throws(() => parseCatalogPage(page([mouse],0),1));
});

test("BKL-019 rechaza respuestas de página incoherentes", () => {
  for (const value of [
    {...page(), size:101},
    {...page(), totalElements:0},
    {...page(), totalPages:0},
    {...page(), items:"string"},
    {...page(), items:[{...mouse,active:false}]},
  ]) assert.throws(() => parseCatalogPage(value,0));
});

test("BKL-019 acepta paginación de API desde cero y rechaza ataques", () => {
  assert.equal(parseCatalogPageQuery(null),0);
  assert.equal(parseCatalogPageQuery("2"),2);
  for(const value of ["-1","1.5","01","1e2","abc","999999999999999999"]) {
    assert.equal(parseCatalogPageQuery(value),null);
  }
  assert.equal(parseProductId("15"),15);
  for(const id of ["0","-1","abc","1.4","9007199254740993"]) {
    assert.equal(parseProductId(id),null);
  }
});

test("BKL-019 precios en colones con decimales y sin precios del navegador", () => {
  assert.match(formatCatalogCrc(12990.5),/12[.,\s]990|12\s990/);
  assert.match(formatCatalogCrc(12990.5),/₡|CRC/);
});
