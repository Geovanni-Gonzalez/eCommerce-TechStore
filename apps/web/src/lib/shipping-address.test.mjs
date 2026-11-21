import test from "node:test";
import assert from "node:assert/strict";
import {
  COSTA_RICA_PROVINCES, EMPTY_SHIPPING_ADDRESS,
  normalizeShippingAddress, validateShippingAddress, makeShippingAddressBody,
} from "./shipping-address.ts";
const valid = {
  province:"San José",canton:"Central",district:"Carmen",
  details:"Frente a la plaza central, casa azul",
  recipientName:"Ana Pérez",phone:"8888-1111",
};
test("BKL-031 siete provincias de Costa Rica",()=>{
  assert.deepEqual(COSTA_RICA_PROVINCES,[
    "San José","Alajuela","Cartago","Heredia",
    "Guanacaste","Puntarenas","Limón",
  ]);
});
test("BKL-031 valida, normaliza y prepara solo shippingAddress",()=>{
  const normalized = normalizeShippingAddress({...valid,details:"  Casa azul  "});
  assert.equal(normalized.details,"Casa azul");
  assert.deepEqual(validateShippingAddress(normalized),{});
  assert.deepEqual(makeShippingAddressBody(normalized),{shippingAddress:normalized});
});
test("BKL-031 presenta errores específicos por seis campos vacíos",()=>{
  assert.deepEqual(Object.keys(validateShippingAddress(EMPTY_SHIPPING_ADDRESS)).sort(),
    ["province","canton","district","details","recipientName","phone"].sort());
});
test("BKL-031 rechaza provincias ajenas sin afirmar validación de cantones",()=>{
  assert.ok(validateShippingAddress({...valid,province:"Fuera de CR"}).province);
  assert.deepEqual(validateShippingAddress({
    ...valid,canton:"Pérez Zeledón",district:"San Isidro de El General",
  }),{});
});
test("BKL-031 límites y caracteres de control del backend Java",()=>{
  for (const item of [
    {...valid,canton:"x".repeat(81)},
    {...valid,district:"\tDistrito"},
    {...valid,details:"1234"},
    {...valid,details:"x".repeat(301)},
    {...valid,recipientName:"A"},
    {...valid,recipientName:"A".repeat(121)},
    {...valid,phone:"1234567"},
    {...valid,phone:"1".repeat(25)},
    {...valid,phone:"8888\n1111"},
  ]) {
    assert.ok(Object.keys(validateShippingAddress(item)).length > 0);
    assert.throws(()=>makeShippingAddressBody(item));
  }
});
test("BKL-031 no agrega userId, precio, cartId, ni cobro al DTO",()=>{
  const body=makeShippingAddressBody(valid);
  assert.deepEqual(Object.keys(body),["shippingAddress"]);
  assert.deepEqual(Object.keys(body.shippingAddress).sort(),
    ["province","canton","district","details","recipientName","phone"].sort());
  assert.equal(JSON.stringify(body).includes("totalCrc"),false);
});
