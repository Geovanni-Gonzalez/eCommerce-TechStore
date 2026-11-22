import test from "node:test";
import assert from "node:assert/strict";
import {
  parseOrderSummary, parseOrderPage, parseOrderDetail, parseOrderHistory,
  isOrderId, STATUS_COPY, REASON_COPY, formatCrc, formatOrderDate,
} from "./order-history.ts";
import {filterLoadedOrders, loadedOrderCounts} from "./order-list-view.ts";

const totals = { subtotalCrc: 1250, shippingCrc: 2500, taxCrc: 162.5, totalCrc: 3912.5 };
const summary = (id=15, status="PENDING") => ({
  id, status, paymentMode:"SIMULATED", totals, createdAt:"2026-11-21T18:00:00Z",
});
const detail = (id=15) => ({
  ...summary(id),
  items:[{productId:5,name:"Mouse antes del cambio de catálogo",quantity:1,
    unitPriceCrc:1250,lineTotalCrc:1250}],
  shippingAddress: {
    province:"San José",canton:"Central",district:"Carmen",
    details:"Casa azul junto al parque",recipientName:"Ana",phone:"88881111",
  },
});
const history = [
  {fromStatus:null,toStatus:"PENDING",actorType:"CLIENT",reason:"CREATED",
    changedAt:"2026-11-21T18:00:00Z"},
  {fromStatus:"PENDING",toStatus:"CONFIRMED",actorType:"SYSTEM",
    reason:"SANDBOX_VERIFIED",changedAt:"2026-11-21T18:02:00Z"},
];

test("BKL-043 acepta PENDING, CONFIRMED y CANCELLED con textos sin cobro real", () => {
  for (const status of ["PENDING","CONFIRMED","CANCELLED"]) {
    assert.equal(parseOrderSummary(summary(15,status)).status,status);
    assert.ok(STATUS_COPY[status].description.length>35);
  }
  assert.match(STATUS_COPY.CONFIRMED.description,/no demuestra un cobro bancario real/);
  assert.match(STATUS_COPY.PENDING.description,/No se presume un pago/);
});

test("BKL-043 normaliza páginas por cursor estricto y sin propietarIo", () => {
  const page = parseOrderPage({items:[summary(21),summary(17)],hasMore:true,nextBeforeId:17});
  assert.deepEqual(page.items.map(o=>o.id),[21,17]);
  assert.equal(page.nextBeforeId,17);
  assert.equal("userId" in page.items[0],false);
  assert.deepEqual(parseOrderPage({items:[],hasMore:false,nextBeforeId:null}),
    {items:[],hasMore:false,nextBeforeId:null});
});

test("BKL-043 rechaza cursores falsos, duplicados, desordenados y contenido malicioso", () => {
  for (const value of [
    {items:[summary(21),summary(17)],hasMore:true,nextBeforeId:21},
    {items:[summary(21),summary(21)],hasMore:false,nextBeforeId:null},
    {items:[summary(17),summary(21)],hasMore:false,nextBeforeId:null},
    {items:[],hasMore:true,nextBeforeId:4},
    {items:[summary(21)],hasMore:false,nextBeforeId:21},
    {items:"no es arreglo",hasMore:false,nextBeforeId:null},
    null,
  ]) assert.throws(()=>parseOrderPage(value));
});

test("BKL-043 snapshots históricos incluyen líneas, dirección y totales propios", () => {
  const order=parseOrderDetail(detail(),15);
  assert.equal(order.items[0].name,"Mouse antes del cambio de catálogo");
  assert.equal(order.shippingAddress.details,"Casa azul junto al parque");
  assert.equal(order.totals.totalCrc,3912.5);
  assert.equal("userId" in order,false);
});

test("BKL-043 la UI no presenta detalles ajenos ni datos incompatibles", () => {
  assert.throws(()=>parseOrderDetail(detail(88),15));
  assert.throws(()=>parseOrderDetail({...detail(),items:[]},15));
  assert.throws(()=>parseOrderDetail({...detail(),items:[{productId:5,name:"X",quantity:-1,
    unitPriceCrc:1,lineTotalCrc:1}]},15));
  assert.throws(()=>parseOrderDetail({...detail(),shippingAddress:{phone:"8888"}},15));
  assert.throws(()=>parseOrderDetail({...detail(),status:"PAID"},15));
  assert.throws(()=>parseOrderDetail({...detail(),paymentMode:"REAL"},15));
  assert.throws(()=>parseOrderSummary({...summary(),totals:{...totals,totalCrc:-2}}));
});

test("BKL-043 permite historial verificable sin exponer actorId ni IDs internos", () => {
  const list=parseOrderHistory(history);
  assert.equal(list.length,2);
  assert.equal(list[1].reason,"SANDBOX_VERIFIED");
  assert.equal(list[1].toStatus,"CONFIRMED");
  assert.equal("actorId" in list[1],false);
  assert.equal("orderId" in list[1],false);
  assert.equal("id" in list[1],false);
});

test("BKL-043 rechaza historial adulterado o estados inventados", () => {
  for (const entry of [
    {...history[1],reason:"BANK_SETTLED"},
    {...history[1],actorType:"INTRUDER"},
    {...history[1],toStatus:"PAID"},
    {...history[1],changedAt:"incorrecto"},
  ]) assert.throws(()=>parseOrderHistory([entry]));
  assert.throws(()=>parseOrderHistory({items:history}));
});

test("BKL-043 usa códigos reales de actor y motivos de auditoría", () => {
  assert.equal(REASON_COPY.SANDBOX_VERIFIED,"Confirmación de simulación verificada");
  assert.equal(REASON_COPY.EXPIRED,"Reserva vencida");
  assert.equal(REASON_COPY.ADMIN_CANCEL,"Cancelado por administración");
});

test("BKL-043 rechaza identificadores inseguros y fechas inválidas", () => {
  for(const id of [0,-1,1.1,NaN,Infinity,Number.MAX_SAFE_INTEGER+1,"15"]) {
    assert.equal(isOrderId(id),false);
  }
  assert.throws(()=>parseOrderSummary({...summary(),createdAt:"ayer"}));
  assert.ok(formatOrderDate("2026-11-21T18:00:00Z").length > 6);
  assert.match(formatCrc(1500.25),/1[.,]500|1\s500/);
});

test("Mis pedidos: los indicadores cuentan solo las filas cargadas", () => {
  const list=[summary(31,"PENDING"),summary(25,"CONFIRMED"),
    summary(17,"CANCELLED"),summary(8,"PENDING")];
  assert.deepEqual(loadedOrderCounts(list),{
    loaded:4,pending:2,confirmed:1,cancelled:1,
  });
  assert.deepEqual(loadedOrderCounts([]),{
    loaded:0,pending:0,confirmed:0,cancelled:0,
  });
});

test("Mis pedidos: filtros y busqueda son locales, combinables y no mutan el historial", () => {
  const list=[summary(531,"PENDING"),summary(310,"CONFIRMED"),
    summary(105,"PENDING"),summary(23,"CANCELLED")];
  const original=list.map(row=>row.id);
  assert.deepEqual(filterLoadedOrders(list,"ALL","").map(o=>o.id),original);
  assert.deepEqual(filterLoadedOrders(list,"PENDING","").map(o=>o.id),[531,105]);
  assert.deepEqual(filterLoadedOrders(list,"CONFIRMED","#310").map(o=>o.id),[310]);
  assert.deepEqual(filterLoadedOrders(list,"PENDING","  # 1 ").map(o=>o.id),[531,105]);
  assert.deepEqual(filterLoadedOrders(list,"ALL","abc"),[]);
  assert.deepEqual(filterLoadedOrders(list,"ALL","99999999999"),[]);
  assert.deepEqual(list.map(row=>row.id),original);
});
