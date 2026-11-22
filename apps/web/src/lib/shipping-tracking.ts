/**
 * BKL-048: parseo del historial logístico declarado manualmente.
 * No representa verificación de un transportista ni estado de pago.
 */
import {isSafeApiOrigin} from "./payment-result";
const BASE=(process.env.NEXT_PUBLIC_API_URL??"http://localhost:8080").replace(/\/$/,"");
export type ManualShippingStatus="NOT_REGISTERED"|"PENDING_MANUAL"|"IN_TRANSIT"|"DELIVERED";
export type ShipmentHistoryStatus=Exclude<ManualShippingStatus,"NOT_REGISTERED">;
export type ShippingEvent={
  status:ShipmentHistoryStatus;
  event:"MANUAL_REGISTERED"|"MANUAL_DISPATCH"|"MANUAL_DELIVERY";
  changedAt:string;
};
export type ShippingTracking={
  orderId:number;status:ManualShippingStatus;registeredAt:string|null;
  events:ShippingEvent[];
};
export const SHIPPING_COPY:Record<ManualShippingStatus,string>={
  NOT_REGISTERED:"Aún sin gestión logística registrada",
  PENDING_MANUAL:"Preparación manual pendiente",
  IN_TRANSIT:"Despacho declarado por administración",
  DELIVERED:"Entrega declarada por administración",
};
export const SHIPPING_EVENT_COPY:Record<ShippingEvent["event"],string>={
  MANUAL_REGISTERED:"Tarea manual registrada",
  MANUAL_DISPATCH:"Despacho registrado manualmente",
  MANUAL_DELIVERY:"Entrega registrada manualmente",
};
const validId=(x:unknown):x is number=>typeof x==="number"&&Number.isSafeInteger(x)&&x>0;
const date=(x:unknown):x is string=>typeof x==="string"&&Number.isFinite(Date.parse(x));
const obj=(x:unknown):Record<string,unknown>=>{
  if(!x||typeof x!=="object"||Array.isArray(x))
    throw new Error("El seguimiento recibido es inválido.");
  return x as Record<string,unknown>;
};
const status=(s:unknown):s is ManualShippingStatus=>
  ["NOT_REGISTERED","PENDING_MANUAL","IN_TRANSIT","DELIVERED"].includes(String(s));
const event=(x:unknown):ShippingEvent=>{
  const o=obj(x);
  if(!status(o.status)||o.status==="NOT_REGISTERED"
    ||!["MANUAL_REGISTERED","MANUAL_DISPATCH","MANUAL_DELIVERY"].includes(String(o.event))
    ||!date(o.changedAt))
    throw new Error("Evento logístico inválido.");
  return {status:o.status,
    event:o.event as ShippingEvent["event"],changedAt:o.changedAt};
};
export function parseShippingTracking(raw:unknown,orderId:number):ShippingTracking {
  const o=obj(raw);
  if(!validId(orderId)||o.orderId!==orderId||!status(o.status)
    ||!(o.registeredAt===null||date(o.registeredAt))
    ||!Array.isArray(o.events)||o.events.length>3)
    throw new Error("El seguimiento no corresponde al pedido.");
  const events=o.events.map(event);
  const stages:ShipmentHistoryStatus[]=["PENDING_MANUAL","IN_TRANSIT","DELIVERED"];
  if(o.status==="NOT_REGISTERED") {
    if(o.registeredAt!==null||events.length!==0)
      throw new Error("Estado logístico inconsistente.");
  } else {
    const stage=stages.indexOf(o.status);
    if(!date(o.registeredAt)||events.length!==stage+1
      ||events.some((e,i)=>e.status!==stages[i]
        ||e.event!==["MANUAL_REGISTERED","MANUAL_DISPATCH","MANUAL_DELIVERY"][i]))
      throw new Error("Historial logístico inconsistente.");
  }
  return {orderId,status:o.status,registeredAt:o.registeredAt as string|null,events};
}
export async function getCustomerShippingTracking(
  token:string,orderId:number,signal?:AbortSignal):Promise<ShippingTracking> {
  if(!validId(orderId)||!isSafeApiOrigin(BASE)||!/^[A-Za-z0-9_-]{32,}$/.test(token))
    throw new Error("Sesión o ruta de consulta inválidas.");
  const response=await fetch(BASE+"/api/orders/"+orderId+"/shipping",{
    method:"GET",signal,cache:"no-store",credentials:"omit",referrerPolicy:"no-referrer",
    headers:{Authorization:"Bearer "+token,Accept:"application/json"},
  });
  if(!response.ok) {
    throw new Error(response.status===401?"Sesión expirada; inicia sesión otra vez."
      :response.status===403?"No tienes acceso a este seguimiento."
      :response.status===404?"Este pedido no existe o no te pertenece."
      :"No se pudo consultar el seguimiento (HTTP "+response.status+").");
  }
  return parseShippingTracking(await response.json() as unknown,orderId);
}
