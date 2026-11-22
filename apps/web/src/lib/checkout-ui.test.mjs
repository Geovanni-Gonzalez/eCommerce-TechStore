import test from "node:test";
import assert from "node:assert/strict";
import {
  parseCheckoutTotals,parseCheckoutQuote,parseCreatedOrder,
  parseSandboxSession,checkoutAmountsChanged,
} from "./checkout-ui.ts";

const address={
  province:"San José",canton:"Central",district:"Carmen",
  details:"Casa azul frente a plaza",recipientName:"Ana Pérez",phone:"8888-1111",
};
const cart={
  items:[{
    productId:7,sku:"TEST-7",name:"Mouse Demo",quantity:2,stock:10,available:true,
    unitPriceCrc:10000,lineSubtotalCrc:20000,
  }],
  totalItems:2,subtotalCrc:20000,checkoutReady:true,
};
const quote={
  totalItems:2,totals:{
    subtotalCrc:20000,shippingCrc:2500,taxCrc:2600,totalCrc:25100,
  },shippingPolicy:"FIXED",estimateOnly:true,
};
const order={
  id:91,status:"PENDING",paymentMode:"SIMULATED",
  items:[{productId:7,name:"Mouse Demo",quantity:2,
    unitPriceCrc:10000,lineTotalCrc:20000}],
  shippingAddress:address,totals:quote.totals,createdAt:"2026-11-21T10:00:00Z",
};
const session={
  attemptId:33,orderId:91,totalCrc:25100,currency:"CRC",
  expiresAt:"2026-11-21T10:10:00Z",
  redirectUrl:"http://localhost:3000/pagos/sandbox#session="+"a".repeat(43),
};
test("BKL-034 valida cotización sin confiar en precios del navegador",()=>{
  assert.deepEqual(parseCheckoutQuote(quote,cart),quote);
  for(const sample of [
    {...quote,totalItems:3},
    {...quote,estimateOnly:false},
    {...quote,shippingPolicy:"USER_SELECTED"},
    {...quote,totals:{...quote.totals,subtotalCrc:1}},
    {...quote,totals:{...quote.totals,totalCrc:1}},
  ])assert.throws(()=>parseCheckoutQuote(sample,cart));
  assert.throws(()=>parseCheckoutQuote(quote,{...cart,checkoutReady:false}));
});
test("BKL-034 totales CRC requieren suma y centavos exactos",()=>{
  assert.deepEqual(parseCheckoutTotals(quote.totals),quote.totals);
  for(const totals of [
    {...quote.totals,shippingCrc:-1},
    {...quote.totals,totalCrc:25100.001},
    {...quote.totals,totalCrc:25101},
    {...quote.totals,taxCrc:NaN},
  ])assert.throws(()=>parseCheckoutTotals(totals));
});
test("BKL-034 acepta pedido PENDING con snapshot histórico y dirección propia",()=>{
  assert.deepEqual(parseCreatedOrder(order,address),order);
  for(const sample of [
    {...order,status:"CONFIRMED"},
    {...order,shippingAddress:{...address,phone:"12345678"}},
    {...order,items:[{...order.items[0],quantity:99}]},
    {...order,totals:{...quote.totals,totalCrc:99999}},
    {...order,id:"91"},
  ])assert.throws(()=>parseCreatedOrder(sample,address));
});
test("BKL-034 impide redirección externa y filtración de token en query",()=>{
  assert.deepEqual(parseSandboxSession(session,order,"http://localhost:3000"),session);
  for(const url of [
    "https://evil.example/pagos/sandbox#session="+"a".repeat(43),
    "javascript:alert(1)",
    "http://localhost:3000/pagos/sandbox?session=secret",
    "http://localhost:3000/pagos/resultado#session="+"a".repeat(43),
    "http://localhost:3000/pagos/sandbox#session=small",
    "http://localhost:3000/pagos/sandbox#session="+"a".repeat(43)+"&x=1",
    "http://u:p@localhost:3000/pagos/sandbox#session="+"a".repeat(43),
  ]) assert.throws(()=>parseSandboxSession({...session,redirectUrl:url},order,
    "http://localhost:3000"));
  assert.throws(()=>parseSandboxSession(session,order,"http://remote.example"));
});
test("BKL-034 solo acepta sesión del pedido y monto CRC correctos",()=>{
  for(const sample of [
    {...session,orderId:92},
    {...session,totalCrc:123},
    {...session,currency:"USD"},
    {...session,attemptId:0},
  ])assert.throws(()=>parseSandboxSession(sample,order,"http://localhost:3000"));
});
test("BKL-034 detecta cambios de importes definitivos tras confirmar",()=>{
  assert.equal(checkoutAmountsChanged(quote,order),false);
  assert.equal(checkoutAmountsChanged(quote,{
    ...order,totals:{...order.totals,shippingCrc:3000,totalCrc:25600},
  }),true);
});
