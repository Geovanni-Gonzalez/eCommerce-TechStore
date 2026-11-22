"use client";

import {useEffect,useState} from "react";
import {isSafeApiOrigin} from "@/lib/payment-result";
import {formatOrderDate} from "@/lib/order-history";

type OrderNotice = {
  id:number;orderId:number;type:"ORDER_RECEIVED";message:string;createdAt:string;
};
const BASE=(process.env.NEXT_PUBLIC_API_URL??"http://localhost:8080").replace(/\/$/,"");

function parseNotices(value:unknown,orderId:number):OrderNotice[] {
  if(!Array.isArray(value)||value.length>100)throw new Error("Respuesta de avisos inválida.");
  return value.map((item:unknown)=>{
    if(!item||typeof item!=="object")throw new Error("Aviso inválido.");
    const o=item as Record<string,unknown>;
    if(!Number.isSafeInteger(o.id)||o.orderId!==orderId||
        o.type!=="ORDER_RECEIVED"||typeof o.message!=="string"||
        o.message.length>180||typeof o.createdAt!=="string"||
        !Number.isFinite(Date.parse(o.createdAt)))
      throw new Error("Aviso inválido.");
    return {id:o.id as number,orderId,type:"ORDER_RECEIVED" as const,
      message:o.message,createdAt:o.createdAt};
  });
}

/** Solo recibe el getter de token en memoria; no usa cookies ni storage. */
export default function OrderNotificationsPanel({
  orderId,getToken,
}:{orderId:number;getToken:()=>string|null}) {
  const [reload,setReload]=useState(0);
  const [data,setData]=useState<OrderNotice[]|null>(null);
  const [error,setError]=useState("");
  useEffect(()=>{
    const controller=new AbortController();
    const token=getToken();
    if(!token||!isSafeApiOrigin(BASE)){
      void Promise.resolve().then(()=>{
        if(!controller.signal.aborted)setError("No se puede consultar el aviso de este pedido.");
      });
      return ()=>controller.abort();
    }
    void fetch(BASE+"/api/orders/"+orderId+"/notifications",{
      method:"GET",cache:"no-store",credentials:"omit",referrerPolicy:"no-referrer",
      headers:{Authorization:"Bearer "+token,Accept:"application/json"},
      signal:controller.signal,
    }).then(async response=>{
      if(!response.ok)throw new Error(
        response.status===404?"Este pedido ya no está disponible.":
        "No se pudieron consultar las notificaciones.");
      return parseNotices((await response.json()) as unknown,orderId);
    }).then(notices=>{
      if(!controller.signal.aborted&&getToken()===token)setData(notices);
    }).catch((cause:unknown)=>{
      if(!controller.signal.aborted&&getToken()===token)
        setError(cause instanceof Error?cause.message:"No se pudo consultar el aviso.");
    });
    return ()=>controller.abort();
  },[orderId,getToken,reload]);

  return <section className="orders-notices" aria-label="Avisos del pedido">
    <h3>Avisos de tu pedido</h3>
    {!data&&!error&&<p role="status">Cargando avisos…</p>}
    {error&&<p role="alert" className="orders-error">{error}</p>}
    {data?.length===0&&<p className="orders-muted">Aún no hay avisos para este pedido.</p>}
    {data&&data.length>0&&<ol className="orders-timeline" aria-label="Avisos internos">
      {data.map(item=><li key={item.id}>
        <strong>Pedido recibido</strong>
        <p>{item.message}</p>
        <time dateTime={item.createdAt} className="orders-muted">
          {formatOrderDate(item.createdAt)}
        </time>
      </li>)}
    </ol>}
    <p className="orders-footnote">
      Estos son avisos internos de TechStore; no son comprobantes bancarios ni correos enviados.
    </p>
    <button type="button" className="orders-button orders-button--outline"
      onClick={()=>{setData(null);setError("");setReload(n=>n+1);}}>
      Actualizar avisos
    </button>
  </section>;
}
