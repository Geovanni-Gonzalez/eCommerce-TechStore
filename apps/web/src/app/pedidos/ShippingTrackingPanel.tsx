"use client";

import {useEffect,useState} from "react";
import {
  getCustomerShippingTracking,SHIPPING_COPY,SHIPPING_EVENT_COPY,
  type ShippingTracking,
} from "@/lib/shipping-tracking";
import {formatOrderDate} from "@/lib/order-history";

/** Solo usa el token en memoria de la vista Mis pedidos. */
export default function ShippingTrackingPanel({
  getToken,orderId,
}:{getToken:()=>string|null;orderId:number}) {
  const [reload,setReload]=useState(0);
  const [result,setResult]=useState<ShippingTracking|null>(null);
  const [error,setError]=useState("");
  useEffect(()=>{
    const controller=new AbortController();
    const token=getToken();
    if(!token) {
      void Promise.resolve().then(()=>{
        if(!controller.signal.aborted)
          setError("Sesión no disponible para consultar el seguimiento.");
      });
      return ()=>controller.abort();
    }
    void getCustomerShippingTracking(token,orderId,controller.signal).then(data=>{
      if(!controller.signal.aborted)setResult(data);
    }).catch((cause:unknown)=>{
      if(!controller.signal.aborted)setError(cause instanceof Error?cause.message:
        "No se pudo consultar el seguimiento.");
    });
    return ()=>controller.abort();
  },[getToken,orderId,reload]);

  return <section className="orders-shipping" aria-label="Seguimiento de envío">
    <h3>Seguimiento de entrega</h3>
    <p className="orders-muted">
      Los hitos son registros manuales de TechStore, no verificaciones de
      una empresa transportista. No se ofrecen guías ni ubicaciones en tiempo real.
    </p>
    {error&&<p className="orders-error" role="alert">{error}</p>}
    {!result&&!error&&<p role="status">Consultando seguimiento del pedido…</p>}
    {result&&<>
      <p><strong>{SHIPPING_COPY[result.status]}</strong></p>
      {result.status==="NOT_REGISTERED"
        ?<p className="orders-muted">
          Todavía no existe una tarea de preparación de envío para este pedido.
        </p>
        :<ol className="orders-timeline" aria-label="Hitos logísticos">
          {result.events.map((entry,i)=>(
            <li key={entry.event+"-"+i}>
              <strong>{SHIPPING_EVENT_COPY[entry.event]}</strong>
              <span className="orders-muted">
                {formatOrderDate(entry.changedAt)}
              </span>
            </li>
          ))}
        </ol>}
    </>}
    <button type="button" className="orders-button orders-button--outline"
      onClick={()=>{setResult(null);setError("");setReload(v=>v+1);}}>
      Actualizar seguimiento
    </button>
  </section>;
}
