/**
 * BKL-046: API del panel ADMIN. Solo los endpoints existentes de BKL-045/026.
 * Bearer en memoria, no URL, cookies, localStorage ni sessionStorage.
 */
import {isSafeApiOrigin} from "./payment-result";

const BASE=(process.env.NEXT_PUBLIC_API_URL??"http://localhost:8080").replace(/\/$/,"");
const integer=(v:unknown):v is number=>typeof v==="number"&&Number.isSafeInteger(v);
const positive=(v:unknown):v is number=>integer(v)&&v>0;
const amount=(v:unknown):v is number=>typeof v==="number"&&Number.isFinite(v)&&v>=0;
const nonEmpty=(v:unknown):v is string=>typeof v==="string"&&v.trim().length>0;
function object(v:unknown):Record<string,unknown> {
  if(!v||typeof v!=="object"||Array.isArray(v))throw new Error("Respuesta de API inválida.");
  return v as Record<string,unknown>;
}
export class AdminOperationError extends Error {
  constructor(readonly status:number,message:string){super(message);this.name="AdminOperationError";}
}
function failureMessage(status:number):string {
  switch(status){
    case 400:return "Solicitud inválida. Comprueba el formulario o la paginación.";
    case 401:return "Tu sesión caducó. Inicia sesión otra vez.";
    case 403:return "La API rechazó el permiso ADMIN.";
    case 404:return "El pedido o producto ya no existe.";
    case 409:return "El estado o stock cambió, o el identificador ya se usó. Actualiza la información.";
    default:return "No se pudo confirmar la operación. Consulta el historial antes de repetirla.";
  }
}
async function call(token:string,method:"GET"|"POST"|"PATCH",path:string,
  options?:{body?:unknown;signal?:AbortSignal}):Promise<unknown|null> {
  if(!isSafeApiOrigin(BASE)||!/^[A-Za-z0-9_-]{32,}$/.test(token)
      ||!path.startsWith("/api/admin/")) {
    throw new Error("Se requiere conexión segura y sesión administrativa válida.");
  }
  const response=await fetch(BASE+path,{
    method,cache:"no-store",credentials:"omit",referrerPolicy:"no-referrer",
    signal:options?.signal,
    headers:{Authorization:"Bearer "+token,Accept:"application/json",
      ...(options?.body!==undefined?{"Content-Type":"application/json"}:{})},
    ...(options?.body!==undefined?{body:JSON.stringify(options.body)}:{}),
  });
  if(!response.ok)throw new AdminOperationError(response.status,failureMessage(response.status));
  return response.status===204?null:await response.json() as unknown;
}
export type OrderStatus="PENDING"|"CONFIRMED"|"CANCELLED";
export type Totals={subtotalCrc:number;shippingCrc:number;taxCrc:number;totalCrc:number};
export type OrderOverview={
  id:number;status:OrderStatus;paymentMode:string;
  totals:Totals;createdAt:string;
};
export type AdminOrderRow={ownerUserId:number;order:OrderOverview};
export type AdminOrdersPage={items:AdminOrderRow[];hasMore:boolean;nextBeforeId:number|null};
export type OrderLine={productId:number;name:string;quantity:number;unitPriceCrc:number;lineTotalCrc:number};
export type AdminOrderDetail={ownerUserId:number;order:OrderOverview&{items:OrderLine[]}};
export type OrderHistory={
  id:number;orderId:number;fromStatus:OrderStatus|null;toStatus:OrderStatus;
  actorType:string;actorId:number|null;reason:string;changedAt:string;
};
const status=(v:unknown):v is OrderStatus=>v==="PENDING"||v==="CONFIRMED"||v==="CANCELLED";
function totals(v:unknown):Totals {
  const t=object(v);
  if(!amount(t.subtotalCrc)||!amount(t.shippingCrc)||!amount(t.taxCrc)
      ||!amount(t.totalCrc))throw new Error("Importes inválidos.");
  return {subtotalCrc:t.subtotalCrc,shippingCrc:t.shippingCrc,taxCrc:t.taxCrc,totalCrc:t.totalCrc};
}
function order(v:unknown):OrderOverview {
  const o=object(v);
  if(!positive(o.id)||!status(o.status)||!nonEmpty(o.paymentMode)
      ||!nonEmpty(o.createdAt))throw new Error("Pedido administrativo inválido.");
  return {id:o.id,status:o.status,paymentMode:o.paymentMode,createdAt:o.createdAt,
    totals:totals(o.totals)};
}
function row(v:unknown):AdminOrderRow {
  const o=object(v);
  if(!positive(o.ownerUserId))throw new Error("Propietario inválido.");
  return {ownerUserId:o.ownerUserId,order:order(o.order)};
}
export async function adminListOrders(token:string,beforeId:number|null,signal?:AbortSignal) {
  const path="/api/admin/orders?limit=20"+(beforeId?"&beforeId="+beforeId:"");
  const o=object(await call(token,"GET",path,{signal}));
  if(!Array.isArray(o.items)||typeof o.hasMore!=="boolean"
      ||(o.nextBeforeId!==null&&!positive(o.nextBeforeId))
      ||o.hasMore!==(o.nextBeforeId!==null)||o.items.length>20)
    throw new Error("Paginación de pedidos inválida.");
  return {items:o.items.map(row),hasMore:o.hasMore,
    nextBeforeId:o.nextBeforeId as number|null} satisfies AdminOrdersPage;
}
export async function adminOrderDetail(token:string,id:number,signal?:AbortSignal) {
  if(!positive(id))throw new Error("ID inválido.");
  const o=object(await call(token,"GET","/api/admin/orders/"+id,{signal}));
  const base=order(o.order);
  const d=object(o.order);
  if(!positive(o.ownerUserId)||!Array.isArray(d.items))
    throw new Error("Detalle de pedido inválido.");
  const items:OrderLine[]=d.items.map((v:unknown)=>{
    const x=object(v);
    if(!positive(x.productId)||!nonEmpty(x.name)||!positive(x.quantity)
      ||!amount(x.unitPriceCrc)||!amount(x.lineTotalCrc))
      throw new Error("Línea de pedido inválida.");
    return {productId:x.productId,name:x.name,quantity:x.quantity,
      unitPriceCrc:x.unitPriceCrc,lineTotalCrc:x.lineTotalCrc};
  });
  return {ownerUserId:o.ownerUserId,order:{...base,items}} satisfies AdminOrderDetail;
}
export async function adminOrderHistory(token:string,id:number,signal?:AbortSignal) {
  const value=await call(token,"GET","/api/admin/orders/"+id+"/history",{signal});
  if(!Array.isArray(value))throw new Error("Historial inválido.");
  return value.map((v:unknown)=>{
    const x=object(v);
    if(!positive(x.id)||x.orderId!==id||!status(x.toStatus)
      ||(x.fromStatus!==null&&!status(x.fromStatus))
      ||!nonEmpty(x.actorType)||!nonEmpty(x.reason)
      ||!nonEmpty(x.changedAt)
      ||(x.actorId!==null&&!positive(x.actorId))) {
      throw new Error("Entrada de historial inválida.");
    }
    return {id:x.id,orderId:id,toStatus:x.toStatus,
      fromStatus:x.fromStatus as OrderStatus|null,actorType:x.actorType,
      actorId:x.actorId as number|null,reason:x.reason,
      changedAt:x.changedAt} satisfies OrderHistory;
  }) as OrderHistory[];
}
/** Solo PENDING -> CANCELLED, nunca simular CONFIRMED desde UI. */
export async function adminCancelOrder(token:string,id:number) {
  if(!positive(id))throw new Error("ID de pedido inválido.");
  await call(token,"PATCH","/api/admin/orders/"+id+"/status",{body:{status:"CANCELLED"}});
}
export type InventoryBalance={
  productId:number;availableStock:number;reservedStock:number;physicalStock:number;
};
export type InventoryPage={
  items:InventoryBalance[];page:number;size:number;totalElements:number;totalPages:number;
};
export type InventoryMovement={
  id:number;productId:number;orderId:number|null;kind:string;quantity:number;
  availableDelta:number;reservedDelta:number;physicalDelta:number;recordedAt:string;
};
export type InventoryAudit={
  movement:InventoryMovement;actorUserId:number|null;reason:string|null;requestId:string|null;
};
export type InventoryMovementsPage={
  productId:number;items:InventoryAudit[];page:number;size:number;
  totalElements:number;totalPages:number;
};
export type InventoryAdjustmentPayload={
  kind:"ADJUSTMENT_IN"|"ADJUSTMENT_OUT";quantity:number;reason:string;requestId:string;
};
export type InventoryAdjustmentResult={
  audit:InventoryAudit;balance:InventoryBalance;replayed:boolean;
};
function balance(v:unknown):InventoryBalance {
  const b=object(v);
  if(!positive(b.productId)||!integer(b.availableStock)||b.availableStock<0
      ||!integer(b.reservedStock)||b.reservedStock<0
      ||!integer(b.physicalStock)||b.physicalStock<0
      ||b.physicalStock!==b.availableStock+b.reservedStock)
    throw new Error("Balance del servidor inconsistente.");
  return {productId:b.productId,availableStock:b.availableStock,
    reservedStock:b.reservedStock,physicalStock:b.physicalStock};
}
function pageMeta(v:Record<string,unknown>,page:number) {
  if(v.page!==page||v.size!==20||!integer(v.totalElements)
      ||v.totalElements<0||!integer(v.totalPages)||v.totalPages<0)
    throw new Error("Paginación de inventario inválida.");
}
export async function adminListInventory(token:string,page:number,signal?:AbortSignal) {
  const o=object(await call(token,"GET",
    "/api/admin/inventory?page="+page+"&size=20",{signal}));
  pageMeta(o,page);
  if(!Array.isArray(o.items)||o.items.length>20)throw new Error("Lista de inventario inválida.");
  return {items:o.items.map(balance),page,size:20,
    totalElements:o.totalElements as number,totalPages:o.totalPages as number} satisfies InventoryPage;
}
export async function adminGetInventory(token:string,id:number,signal?:AbortSignal) {
  if(!positive(id))throw new Error("ID de producto inválido.");
  return balance(await call(token,"GET","/api/admin/inventory/products/"+id,{signal}));
}
function audit(v:unknown):InventoryAudit {
  const a=object(v),m=object(a.movement);
  if(!positive(m.id)||!positive(m.productId)||!nonEmpty(m.kind)
    ||!positive(m.quantity)||!integer(m.availableDelta)||!integer(m.reservedDelta)
    ||!integer(m.physicalDelta)||!nonEmpty(m.recordedAt)
    ||(m.orderId!==null&&!positive(m.orderId))
    ||(a.actorUserId!==null&&!positive(a.actorUserId))
    ||(a.reason!==null&&typeof a.reason!=="string")
    ||(a.requestId!==null&&typeof a.requestId!=="string"))
    throw new Error("Movimiento de inventario inválido.");
  return {movement:{
    id:m.id,productId:m.productId,kind:m.kind,quantity:m.quantity,
    orderId:m.orderId as number|null,availableDelta:m.availableDelta,
    reservedDelta:m.reservedDelta,physicalDelta:m.physicalDelta,
    recordedAt:m.recordedAt},actorUserId:a.actorUserId as number|null,
    reason:a.reason as string|null,requestId:a.requestId as string|null};
}
export async function adminInventoryMovements(token:string,id:number,page:number,
  signal?:AbortSignal) {
  if(!positive(id))throw new Error("ID de producto inválido.");
  const o=object(await call(token,"GET",
    "/api/admin/inventory/products/"+id+"/movements?page="+page+"&size=20",{signal}));
  pageMeta(o,page);
  if(o.productId!==id||!Array.isArray(o.items)||o.items.length>20)
    throw new Error("Historial de inventario inválido.");
  return {productId:id,items:o.items.map(audit),page,size:20,
    totalElements:o.totalElements as number,totalPages:o.totalPages as number} satisfies InventoryMovementsPage;
}
export async function adminAdjustInventory(token:string,productId:number,
  payload:InventoryAdjustmentPayload):Promise<InventoryAdjustmentResult> {
  if(!positive(productId)||!Number.isSafeInteger(payload.quantity)
      ||payload.quantity<1||payload.quantity>1000000||!nonEmpty(payload.reason)
      ||payload.reason.trim()!==payload.reason||payload.reason.length<5
      ||payload.reason.length>300||/[\x00-\x1f\x7f]/.test(payload.reason)
      ||!["ADJUSTMENT_IN","ADJUSTMENT_OUT"].includes(payload.kind)
      ||!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(payload.requestId))
    throw new Error("Ajuste inválido.");
  const r=object(await call(token,"POST",
    "/api/admin/inventory/products/"+productId+"/adjustments",{body:payload}));
  if(typeof r.replayed!=="boolean")throw new Error("Resultado del ajuste inválido.");
  const b=balance(r.balance),a=audit(r.audit);
  if(b.productId!==productId||a.movement.productId!==productId
      ||a.requestId!==payload.requestId||a.movement.kind!==payload.kind
      ||a.movement.quantity!==payload.quantity)throw new Error("Ajuste devuelto inconsistente.");
  return {balance:b,audit:a,replayed:r.replayed};
}
