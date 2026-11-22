"use client";

import {useEffect,useRef,useState} from "react";
import {useAuth} from "@/components/AuthProvider";
import {
  AdminOperationError,adminAdvanceShipment,adminGetShipment,
  adminRegisterShipment,adminShipmentHistory,
  type AdminShipment,type AdminShipmentHistory,
} from "@/lib/admin-operations";
import {SHIPPING_COPY,SHIPPING_EVENT_COPY} from "@/lib/shipping-tracking";

type Action="REGISTER"|"IN_TRANSIT"|"DELIVERED";
export default function AdminShipmentPanel({token,orderId}:{token:string;orderId:number}) {
  const {getToken,logout}=useAuth();
  const [shipment,setShipment]=useState<AdminShipment|null>(null);
  const [history,setHistory]=useState<AdminShipmentHistory[]>([]);
  const [loading,setLoading]=useState(true);
  const [refresh,setRefresh]=useState(0);
  const [error,setError]=useState("");
  const [notice,setNotice]=useState("");
  const [confirm,setConfirm]=useState<Action|null>(null);
  const [busy,setBusy]=useState(false);
  const running=useRef(false);

  useEffect(()=>{
    const ctrl=new AbortController();
    void (async()=>{
      try{
        const current=await adminGetShipment(token,orderId,ctrl.signal);
        if(ctrl.signal.aborted)return;
        const events=current?await adminShipmentHistory(token,orderId,ctrl.signal):[];
        if(ctrl.signal.aborted)return;
        setShipment(current);setHistory(events);setError("");
      }catch(cause:unknown){
        if(ctrl.signal.aborted)return;
        setError(cause instanceof Error?cause.message:"No se pudo consultar logística.");
        if(cause instanceof AdminOperationError&&cause.status===401)void logout();
      }finally{if(!ctrl.signal.aborted)setLoading(false);}
    })();
    return ()=>ctrl.abort();
  },[token,orderId,refresh,logout]);

  const next:Action|null=!shipment?"REGISTER"
    :shipment.status==="PENDING_MANUAL"?"IN_TRANSIT"
    :shipment.status==="IN_TRANSIT"?"DELIVERED":null;
  const label:Record<Action,string>={
    REGISTER:"Abrir gestión manual",
    IN_TRANSIT:"Registrar despacho físico realizado",
    DELIVERED:"Registrar entrega física realizada",
  };

  async function apply() {
    if(confirm===null||busy||running.current||getToken()!==token)return;
    const action=confirm;
    running.current=true;setBusy(true);setError("");setNotice("");
    try{
      if(action==="REGISTER")await adminRegisterShipment(token,orderId);
      else await adminAdvanceShipment(token,orderId,action);
      if(getToken()===token){
        setNotice("Hito manual registrado. Consulta el historial antes de continuar.");
        setConfirm(null);setLoading(true);setRefresh(x=>x+1);
      }
    }catch(cause:unknown){
      if(getToken()===token){
        setConfirm(null);
        setError(cause instanceof Error?cause.message:"Operación logística no confirmada.");
        if(cause instanceof AdminOperationError&&cause.status===401)void logout();
      }
    }finally{running.current=false;setBusy(false);}
  }

  return <section className="admin-shipping" aria-label="Gestión de envío manual">
    <h3>Seguimiento logístico manual</h3>
    <p className="admin-note">
      Cada hito debe corresponder a una operación física realizada.
      No se integra un transportista ni se generan guías o entregas simuladas.
    </p>
    {error&&<p className="admin-error" role="alert">{error}</p>}
    {notice&&<p className="admin-success" role="status">{notice}</p>}
    {loading?<p role="status">Consultando historial logístico…</p>:(
      <>
        <p><strong>Estado:</strong> {
          shipment?SHIPPING_COPY[shipment.status]:"Sin tarea registrada"
        }</p>
        {history.length>0&&<ol className="admin-audit-list">
          {history.map(e=><li key={e.id}>
            <strong>{SHIPPING_EVENT_COPY[e.reason]}</strong> · ADMIN #{e.actorUserId}
            <small>{new Date(e.changedAt).toLocaleString("es-CR")}</small>
          </li>)}
        </ol>}
        {next&&<div className="admin-actions">
          <button type="button" className="admin-primary" disabled={busy}
            onClick={()=>setConfirm(next)}>{label[next]}</button>
        </div>}
        {!next&&<p className="admin-note">El ciclo manual está finalizado.</p>}
        {confirm&&<div className="admin-confirm" role="group"
          aria-label="Confirmar hito logístico">
          <p>Confirma que {confirm==="REGISTER"
            ?"este pedido debe pasar a gestión manual"
            :confirm==="IN_TRANSIT"
              ?"el pedido fue realmente entregado para despacho"
              :"se ha constatado manualmente la entrega física"}.
            El cambio quedará auditado y visible al cliente; no se puede revertir.</p>
          <div className="admin-actions">
            <button type="button" className="admin-primary" disabled={busy}
              onClick={()=>void apply()}>Confirmar registro manual</button>
            <button type="button" className="admin-secondary" disabled={busy}
              onClick={()=>setConfirm(null)}>Volver</button>
          </div>
        </div>}
      </>
    )}
    <button type="button" className="admin-secondary" disabled={busy||loading}
      onClick={()=>{setLoading(true);setRefresh(x=>x+1);}}>
      Actualizar seguimiento
    </button>
  </section>;
}
