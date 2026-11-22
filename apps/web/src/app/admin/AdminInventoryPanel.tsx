"use client";

import {useEffect,useRef,useState,type FormEvent} from "react";
import {useAuth} from "@/components/AuthProvider";
import {
  AdminOperationError,adminAdjustInventory,adminGetInventory,
  adminInventoryMovements,adminListInventory,
  type InventoryAdjustmentPayload,type InventoryBalance,
  type InventoryMovementsPage,type InventoryPage,
} from "@/lib/admin-operations";

type AdjustmentDraft={kind:"ADJUSTMENT_IN"|"ADJUSTMENT_OUT";quantity:string;reason:string};
const EMPTY:AdjustmentDraft={kind:"ADJUSTMENT_IN",quantity:"",reason:""};

function DateText({value}:{value:string}) {
  const date=new Date(value);
  return <time dateTime={value}>{Number.isFinite(date.getTime())
    ?date.toLocaleString("es-CR"):value}</time>;
}

export default function AdminInventoryPanel({token}:{token:string}) {
  const {getToken,logout}=useAuth();
  const [page,setPage]=useState(0);
  const [refresh,setRefresh]=useState(0);
  const [listing,setListing]=useState<{page:number;data:InventoryPage}|null>(null);
  const [selectedId,setSelectedId]=useState<number|null>(null);
  const [balance,setBalance]=useState<InventoryBalance|null>(null);
  const [movementPage,setMovementPage]=useState(0);
  const [movements,setMovements]=useState<{productId:number;page:number;data:InventoryMovementsPage}|null>(null);
  const [form,setForm]=useState<AdjustmentDraft>(EMPTY);
  const [confirm,setConfirm]=useState(false);
  const [error,setError]=useState("");
  const [detailError,setDetailError]=useState("");
  const [notice,setNotice]=useState("");
  const [busy,setBusy]=useState(false);
  const inFlight=useRef(false);
  const pendingRequest=useRef<{signature:string;requestId:string}|null>(null);

  useEffect(()=>{
    const controller=new AbortController();
    void adminListInventory(token,page,controller.signal).then(data=>{
      if(!controller.signal.aborted)setListing({page,data});
    }).catch((cause:unknown)=>{
      if(!controller.signal.aborted){
        setError(cause instanceof Error?cause.message:"Error al consultar inventario.");
        if(cause instanceof AdminOperationError&&cause.status===401)void logout();
      }
    });
    return ()=>controller.abort();
  },[token,page,refresh,logout]);

  useEffect(()=>{
    if(selectedId===null)return;
    const controller=new AbortController();
    void Promise.all([
      adminGetInventory(token,selectedId,controller.signal),
      adminInventoryMovements(token,selectedId,movementPage,controller.signal),
    ]).then(([balance,data])=>{
      if(!controller.signal.aborted){
        setBalance(balance);setMovements({productId:selectedId,page:movementPage,data});
      }
    }).catch((cause:unknown)=>{
      if(!controller.signal.aborted){
        setDetailError(cause instanceof Error?cause.message:"No se pudo cargar el producto.");
        if(cause instanceof AdminOperationError&&cause.status===401)void logout();
      }
    });
    return ()=>controller.abort();
  },[token,selectedId,movementPage,refresh,logout]);

  function refreshData() {
    if(busy)return;
    setError("");setDetailError("");setListing(null);setMovements(null);
    setRefresh(v=>v+1);
  }
  function selectProduct(id:number) {
    setSelectedId(id);setBalance(null);setMovements(null);
    setMovementPage(0);setForm(EMPTY);setConfirm(false);
    setError("");setDetailError("");setNotice("");pendingRequest.current=null;
  }
  function editDraft(next:AdjustmentDraft) {
    setForm(next);setConfirm(false);setNotice("");
    pendingRequest.current=null; // Cualquier ajuste distinto requiere nuevo UUID.
  }
  const list=listing?.page===page?listing.data:null;
  const current=movements?.productId===selectedId&&movements.page===movementPage
    ?movements.data:null;
  const selectedBalance=balance?.productId===selectedId?balance:null;

  async function perform(event?:FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    if(!confirm||selectedId===null||busy||inFlight.current||getToken()!==token)return;
    const q=Number(form.quantity);
    if(!/^[1-9][0-9]*$/.test(form.quantity)
        ||!Number.isSafeInteger(q)||q>1_000_000
        ||form.reason.length<5||form.reason.length>300
        ||form.reason.trim()!==form.reason||/[\x00-\x1f\x7f]/.test(form.reason)) {
      setError("Indica una cantidad entera de 1 a 1 000 000 y un motivo de 5 a 300 caracteres.");
      setConfirm(false);return;
    }
    const signature=selectedId+"|"+form.kind+"|"+q+"|"+form.reason;
    const record=pendingRequest.current?.signature===signature
      ?pendingRequest.current
      :{signature,requestId:crypto.randomUUID()};
    pendingRequest.current=record;
    const payload:InventoryAdjustmentPayload={
      kind:form.kind,quantity:q,reason:form.reason,requestId:record.requestId,
    };
    inFlight.current=true;setBusy(true);setError("");setNotice("");
    try {
      const result=await adminAdjustInventory(token,selectedId,payload);
      if(getToken()!==token)return;
      setNotice((result.replayed?"Solicitud ya aplicada. ":"Ajuste registrado. ")+
        "Disponible: "+result.balance.availableStock+
        ", reservado: "+result.balance.reservedStock+
        ", físico: "+result.balance.physicalStock+".");
      pendingRequest.current=null;setConfirm(false);setForm(EMPTY);
      setListing(null);setMovements(null);setRefresh(v=>v+1);
    } catch(cause:unknown) {
      if(getToken()!==token)return;
      setError(cause instanceof AdminOperationError&&cause.status===409
        ?"Conflicto de stock o de requestId. No cambies la clave para reintentar sin revisar el historial."
        :"No hay confirmación del ajuste. Verifica el historial; si reintentas, se usará el mismo requestId.");
      // Mantener pendingRequest (idempotencia) si la respuesta se perdió.
      if(cause instanceof AdminOperationError&&cause.status===401)void logout();
      setConfirm(false);
    } finally {inFlight.current=false;setBusy(false);}
  }

  return <div className="admin-management" aria-label="Administrar inventario">
    <section className="admin-panel">
      <div className="admin-section-heading">
        <div>
          <h2>Inventario y existencias</h2>
          <p className="admin-note">Stock físico = disponible + reservado.
            Las reservas de pedidos no se pueden ajustar directamente.</p>
        </div>
        <button type="button" className="admin-secondary" disabled={busy}
          onClick={refreshData}>Actualizar inventario</button>
      </div>
      {error&&<p className="admin-error" role="alert">{error}</p>}
      {notice&&<p className="admin-success" role="status">{notice}</p>}
      {!list?<p role="status">Cargando inventario…</p>:list.items.length===0
        ?<p>Sin productos en esta página.</p>:(
        <div className="admin-table-scroller">
          <table className="admin-table">
            <thead><tr><th scope="col">Producto</th><th scope="col">Físico</th>
              <th scope="col">Reservado</th><th scope="col">Disponible</th>
              <th scope="col">Acción</th></tr></thead>
            <tbody>{list.items.map(item=>(
              <tr key={item.productId}>
                <td>#{item.productId}</td>
                <td>{item.physicalStock}</td><td>{item.reservedStock}</td>
                <td>{item.availableStock}</td>
                <td><button className="admin-secondary" type="button"
                  aria-pressed={selectedId===item.productId} disabled={busy}
                  onClick={()=>selectProduct(item.productId)}>Movimientos y ajuste</button></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {list&&<nav className="admin-actions" aria-label="Paginación de inventario">
        <button className="admin-secondary" type="button" disabled={busy||page===0}
          onClick={()=>{setListing(null);setPage(p=>p-1);}}>Anterior</button>
        <span>Página {page+1} de {Math.max(1,list.totalPages)}</span>
        <button className="admin-secondary" type="button"
          disabled={busy||page+1>=list.totalPages}
          onClick={()=>{setListing(null);setPage(p=>p+1);}}>Siguiente</button>
      </nav>}
    </section>

    {selectedId!==null&&<section className="admin-panel"
      aria-labelledby="admin-inventory-detail-heading">
      <div className="admin-section-heading">
        <div>
          <h2 id="admin-inventory-detail-heading">Producto #{selectedId}</h2>
          {selectedBalance&&<p>
            Físico <strong>{selectedBalance.physicalStock}</strong> ·
            Reservado <strong>{selectedBalance.reservedStock}</strong> ·
            Disponible <strong>{selectedBalance.availableStock}</strong>
          </p>}
        </div>
        <button className="admin-secondary" type="button" disabled={busy}
          onClick={()=>{setSelectedId(null);setConfirm(false);
            pendingRequest.current=null;}}>Cerrar</button>
      </div>
      {detailError&&<p className="admin-error" role="alert">{detailError}</p>}
      {!current&&!detailError&&<p role="status">Cargando saldo e historial…</p>}

      <form className="admin-form admin-adjustment" onSubmit={event=>{
        event.preventDefault();if(!busy)setConfirm(true);
      }}>
        <div className="admin-field">
          <label htmlFor="admin-stock-kind">Movimiento de stock</label>
          <select id="admin-stock-kind" disabled={busy} value={form.kind}
            onChange={event=>editDraft({...form,kind:event.target.value as AdjustmentDraft["kind"]})}>
            <option value="ADJUSTMENT_IN">Entrada (incrementar disponible)</option>
            <option value="ADJUSTMENT_OUT">Salida (descontar disponible)</option>
          </select>
        </div>
        <div className="admin-field">
          <label htmlFor="admin-stock-quantity">Unidades</label>
          <input id="admin-stock-quantity" type="number" min="1" max="1000000"
            step="1" inputMode="numeric" required disabled={busy}
            value={form.quantity}
            onChange={event=>editDraft({...form,quantity:event.target.value})}/>
        </div>
        <div className="admin-field admin-field--wide">
          <label htmlFor="admin-stock-reason">Motivo del ajuste</label>
          <textarea id="admin-stock-reason" required minLength={5} maxLength={300}
            rows={3} disabled={busy} value={form.reason}
            placeholder="Ej. Recepción documentada de mercancía"
            onChange={event=>editDraft({...form,reason:event.target.value})}/>
        </div>
        <div className="admin-actions">
          <button className="admin-primary" type="submit" disabled={busy||!selectedBalance}>
            Revisar ajuste
          </button>
        </div>
      </form>
      {confirm&&<div className="admin-confirm" role="group"
        aria-label="Confirmación de ajuste de inventario">
        <p>¿Aplicar {form.kind==="ADJUSTMENT_IN"?"una entrada":"una salida"} de
          <strong> {form.quantity} unidades</strong> para el producto #{selectedId}?
          El movimiento y el motivo quedarán auditados. No modifica reservas.</p>
        <div className="admin-actions">
          <button className="admin-primary" type="button" disabled={busy}
            onClick={()=>void perform()}>Confirmar ajuste transaccional</button>
          <button className="admin-secondary" type="button" disabled={busy}
            onClick={()=>setConfirm(false)}>Volver a editar</button>
        </div>
      </div>}

      <h3>Historial de movimientos</h3>
      {current&&current.items.length===0&&<p>No hay movimientos en esta página.</p>}
      {current&&current.items.length>0&&(
        <div className="admin-table-scroller">
          <table className="admin-table">
            <thead><tr><th scope="col">Fecha</th><th scope="col">Movimiento</th>
              <th scope="col">Unidades</th><th scope="col">Variaciones</th>
              <th scope="col">Referencia</th></tr></thead>
            <tbody>{current.items.map(({movement:m,actorUserId,reason})=>(
              <tr key={m.id}>
                <td><DateText value={m.recordedAt}/></td>
                <td>{m.kind}</td>
                <td>{m.quantity}</td>
                <td>
                  <small>Disponible {m.availableDelta>0?"+":""}{m.availableDelta}</small>
                  <small>Reservado {m.reservedDelta>0?"+":""}{m.reservedDelta}</small>
                  <small>Físico {m.physicalDelta>0?"+":""}{m.physicalDelta}</small>
                </td>
                <td>{m.orderId!==null?"Pedido #"+m.orderId:
                  actorUserId!==null?"ADMIN #"+actorUserId:"Apertura"}
                  {reason&&<small>{reason}</small>}
                </td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {current&&<nav className="admin-actions" aria-label="Paginación de movimientos">
        <button className="admin-secondary" type="button"
          disabled={busy||movementPage===0}
          onClick={()=>{setMovements(null);setMovementPage(p=>p-1);}}>Anterior</button>
        <span>Página {movementPage+1} de {Math.max(1,current.totalPages)}</span>
        <button className="admin-secondary" type="button"
          disabled={busy||movementPage+1>=current.totalPages}
          onClick={()=>{setMovements(null);setMovementPage(p=>p+1);}}>Siguiente</button>
      </nav>}
    </section>}
  </div>;
}
