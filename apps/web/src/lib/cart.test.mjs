import test from "node:test";
import assert from "node:assert/strict";
import { parseCart, parseCartQuantity, validProductId } from "./cart.ts";

const mouse = {
  productId: 5, sku: "DEMO-MOUSE-01", name: "Mouse Demo",
  quantity: 2, stock: 15, available: true,
  unitPriceCrc: 12990.5, lineSubtotalCrc: 25981,
};
const response = (items = [mouse]) => ({
  items, totalItems: items.reduce((n, item) => n + item.quantity, 0),
  subtotalCrc: items.reduce((n, item) => n + item.lineSubtotalCrc, 0),
  checkoutReady: items.length > 0 && items.every(item => item.available),
});
test("BKL-029 acepta carrito persistente con precios y subtotal desde backend", () => {
  const cart = parseCart(response());
  assert.equal(cart.totalItems, 2);
  assert.equal(cart.subtotalCrc, 25981);
  assert.equal(cart.items[0].productId, 5);
  assert.equal(cart.checkoutReady, true);
});
test("BKL-029 carrito vacío no está listo para checkout", () => {
  assert.deepEqual(parseCart(response([])), {
    items: [], totalItems: 0, subtotalCrc: 0, checkoutReady: false,
  });
});
test("BKL-029 línea sin stock sigue visible pero impide checkout", () => {
  const item = {...mouse, stock: 0, available: false};
  const cart = parseCart(response([item]));
  assert.equal(cart.items[0].available, false);
  assert.equal(cart.checkoutReady, false);
});
test("BKL-029 rechaza precios, subtotales y totales inconsistentes", () => {
  for (const bad of [
    {...response(), subtotalCrc: 1},
    {...response(), totalItems: 1},
    {...response(), checkoutReady: false},
    response([{...mouse, lineSubtotalCrc: 25982}]),
    response([{...mouse, unitPriceCrc: -1}]),
    response([{...mouse, available: true, stock: 0}]),
  ]) assert.throws(() => parseCart(bad));
});
test("BKL-029 rechaza duplicados, tipos inválidos y cantidades desbordadas", () => {
  for (const bad of [
    response([mouse, mouse]),
    response([{...mouse, productId: -1}]),
    response([{...mouse, quantity: 100}]),
    response([{...mouse, quantity: 1.5}]),
    response([{...mouse, name: ""}]),
    {...response(), items: "incorrecto"},
  ]) assert.throws(() => parseCart(bad));
});
test("BKL-029 permite únicamente cantidades enteras 1..99", () => {
  assert.equal(parseCartQuantity("1"), 1);
  assert.equal(parseCartQuantity("99"), 99);
  for (const value of ["", "0", "100", "-1", "1.5", "01", "1e2", "abc", " 2 "]) {
    assert.equal(parseCartQuantity(value), null);
  }
  assert.equal(validProductId(13), true);
  assert.equal(validProductId(0), false);
  assert.equal(validProductId("13"), false);
});
