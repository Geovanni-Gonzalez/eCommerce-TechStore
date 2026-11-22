"use client";

import {useEffect,useRef,useState} from "react";
import {formatCatalogCrc} from "@/lib/catalog";
import {useAuth} from "@/components/AuthProvider";
import {
  AdminOperationError,adminCancelOrder,adminListOrders,adminOrderDetail,
  adminOrderHistory,type AdminOrderDetail,type AdminOrdersPage,type OrderHistory,
} from "@/lib/admin-operations";

function DateText({value}:{value:string}) {
  const date=new Date(value);
  return <time dateTime={value}>{Number.isFinite(date.getTime())
    ?date.toLocaleString("es-CR"):value}</time>;
}

export default function AdminOrdersPanel({token}:{token:string}) {
  const {getToken,logout}=useAuth();
  const [cursorStack,setCursorStack]=useState<Array<number|null>>([null]);
  const [lookup,setLookup]=useState("");
  const [data,setData]=useState<{cursor:number|null;page:AdminOrdersPage}|null>(null);
  const [selectedId,setSelectedId]=useState<number|null>(null);
  const [selected,setSelected]=useState<{id:number;detail:AdminOrderDetail;history:OrderHistory[]}|null>(null);
  const [refresh,setRefresh]=useState(0);
  const [error,setError]=useState("");
  const [detailError,setDetailError]=useState("");
  const [notice,setNotice]=useState("");
  const [busy,setBusy]=useState(false);
  const [confirmId,setConfirmId]=useState<number|null>(null);
  const inFlight=useRef(false);
  const cursor=cursorStack[cursorStack.length-1];

  useEffect(()=>{
    const controller=new AbortController();
    void adminListOrders(token,cursor,controller.signal).then(page=>{
      if(!controller.signal.aborted)setData({cursor,page});
    }).catch((cause:unknown)=>{
      if(!controller.signal.aborted){
        setError(cause instanceof Error?cause.message:"No se pudo cargar pedidos.");
        if(cause instanceof AdminOperationError&&cause.status===401)void logout();
      }
    });
    return ()=>controller.abort();
  },[token,cursor,refresh,logout]);

  useEffect(()=>{
    if(selectedId===null)return;
    const controller=new AbortController();
    void Promise.all([
      adminOrderDetail(token,selectedId,controller.signal),
      adminOrderHistory(token,selectedId,controller.signal),
    ]).then(([detail,history])=>{
      if(!controller.signal.aborted)setSelected({id:selectedId,detail,history});
    }).catch((cause:unknown)=>{
      if(!controller.signal.aborted){
        setDetailError(cause instanceof Error?cause.message:"No se pudo obtener detalle.");
        if(cause instanceof AdminOperationError&&cause.status===401)void logout();
      }
    });
    return ()=>controller.abort();
  },[token,selectedId,refresh,logout]);

  function reload() {
    if(busy)return;
    setError("");setDetailError("");setNotice("");setData(null);setSelected(null);
    setRefresh(v=>v+1);
  }

  async function cancelOrder() {
    if(confirmId===null||busy||inFlight.current||getToken()!==token)return;
    const id=confirmId;
    inFlight.current=true;setBusy(true);setError("");setNotice("");
    try {
      await adminCancelOrder(token,id);
      if(getToken()===token) {
        setNotice("Pedido #"+id+" cancelado; la API liberó las reservas.");
        setConfirmId(null);setData(null);setSelected(null);
        setRefresh(v=>v+1);
      }
    } catch(cause:unknown) {
      if(getToken()===token){
        setConfirmId(null);
        setError(cause instanceof AdminOperationError&&cause.status===409
          ?"El pedido cambió de estado o no admite cancelación. Actualiza su historial."
          :"No se puede confirmar el resultado. Consulta el estado del pedido antes de repetir la acción.");
        if(cause instanceof AdminOperationError&&cause.status===401)void logout();
      }
    } finally {inFlight.current=false;setBusy(false);}
  }
  const page=data?.cursor===cursor?data.page:null;
  const current=selected?.id===selectedId?selected:null;

  return (
    <div className="admin-management" aria-label="Administrar pedidos">
      <section className="admin-panel">
        <div className="admin-section-heading">
          <div><h2>Pedidos de clientes</h2>
            <p className="admin-note">Más recientes primero. Solo una orden PENDING puede
              cancelarse; el pago confirmado se concilia fuera del panel.</p>
          </div>
          <button className="admin-secondary" type="button"
            disabled={busy} onClick={reload}>Actualizar pedidos</button>
        </div>
        <form className="admin-actions" aria-label="Buscar pedido por identificador"
          onSubmit={event=>{
            event.preventDefault();
            const trimmed=lookup.trim();
            if(!/^[1-9][0-9]*$/.test(trimmed)||!Number.isSafeInteger(Number(trimmed))){
              setError("Introduce un ID de pedido entero positivo.");
              return;
            }
            setError("");setNotice("");setConfirmId(null);
            setDetailError("");setSelected(null);setSelectedId(Number(trimmed));
          }}>
          <label htmlFor="admin-order-lookup">Consultar pedido por ID</label>
          <input id="admin-order-lookup" className="admin-short-input" inputMode="numeric"
            placeholder="Ej. 102" value={lookup} maxLength={16}
            disabled={busy} onChange={event=>setLookup(event.target.value)}/>
          <button className="admin-secondary" type="submit" disabled={busy}>
            Buscar pedido
          </button>
        </form>
        {error&&<p className="admin-error" role="alert">{error}</p>}
        {notice&&<p className="admin-success" role="status">{notice}</p>}
        {!page?<p role="status">Cargando pedidos…</p>:page.items.length===0
          ?<p>No hay pedidos para esta página.</p>:(
          <div className="admin-table-scroller">
            <table className="admin-table">
              <thead><tr><th scope="col">Pedido</th><th scope="col">Cliente</th>
                <th scope="col">Fecha</th><th scope="col">Estado</th>
                <th scope="col">Total</th><th scope="col">Acción</th></tr></thead>
              <tbody>{page.items.map(({ownerUserId,order})=>(
                <tr key={order.id}>
                  <td>#{order.id}</td>
                  <td>Usuario #{ownerUserId}</td>
                  <td><DateText value={order.createdAt}/></td>
                  <td><span className={"admin-state admin-state--"+order.status.toLowerCase()}>
                    {order.status}</span></td>
                  <td>{formatCatalogCrc(order.totals.totalCrc)}</td>
                  <td><button type="button" className="admin-secondary"
                    disabled={busy} aria-pressed={selectedId===order.id}
                    onClick={()=>{
                      setSelectedId(order.id);setSelected(null);
                      setDetailError("");setConfirmId(null);
                    }}>Ver detalle</button></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
        {page&&<nav className="admin-actions" aria-label="Paginación de pedidos">
          <button className="admin-secondary" disabled={cursorStack.length===1||busy}
            type="button" onClick={()=>{
              setData(null);setCursorStack(s=>s.slice(0,-1));
            }}>Anterior</button>
          <span>Página {cursorStack.length}</span>
          <button className="admin-secondary" type="button"
            disabled={!page.hasMore||page.nextBeforeId===null||busy}
            onClick={()=>{
              if(page.nextBeforeId!==null){
                setData(null);setCursorStack(s=>[...s,page.nextBeforeId]);
              }
            }}>Siguiente</button>
        </nav>}
      </section>

      {selectedId!==null&&<section className="admin-panel"
        aria-labelledby="admin-order-detail-heading">
        <div className="admin-section-heading">
          <h2 id="admin-order-detail-heading">Detalle del pedido #{selectedId}</h2>
          <button className="admin-secondary" type="button" onClick={()=>{
            setSelectedId(null);setSelected(null);setConfirmId(null);
          }}>Cerrar detalle</button>
        </div>
        {detailError&&<p role="alert" className="admin-error">{detailError}</p>}
        {!current&&!detailError&&<p role="status">Cargando detalle e historial…</p>}
        {current&&<>
          <p><strong>Cliente:</strong> usuario #{current.detail.ownerUserId} ·
            <strong> Estado:</strong> {current.detail.order.status} ·
            <strong> Pago:</strong> {current.detail.order.paymentMode}</p>
          <p><strong>Total confirmado por el servidor:</strong> {
            formatCatalogCrc(current.detail.order.totals.totalCrc)}</p>
          <h3>Dirección de entrega registrada</h3>
          <address className="admin-shipping-address">
            <strong>{current.detail.order.shippingAddress.recipientName}</strong>
            <span>{current.detail.order.shippingAddress.phone}</span>
            <span>{current.detail.order.shippingAddress.province},{" "}
              {current.detail.order.shippingAddress.canton},{" "}
              {current.detail.order.shippingAddress.district}</span>
            <span>{current.detail.order.shippingAddress.details}</span>
          </address>
          <h3>Artículos del pedido</h3>
          <ul className="admin-order-lines">
            {current.detail.order.items.map((item,i)=>(
              <li key={item.productId+"-"+i}>
                {item.name} · {item.quantity} ud. · {
                  formatCatalogCrc(item.lineTotalCrc)}
              </li>
            ))}
          </ul>
          {current.detail.order.status==="PENDING"&&<div className="admin-actions">
            <button className="admin-primary" type="button" disabled={busy}
              onClick={()=>setConfirmId(current.detail.order.id)}>
              Cancelar pedido pendiente
            </button>
          </div>}
          {current.detail.order.status!=="PENDING"&&
            <p className="admin-note">No se permiten cambios manuales desde este estado.</p>}
          {confirmId===current.detail.order.id&&<div className="admin-confirm"
            role="group" aria-label="Confirmar cancelación del pedido">
            <p>¿Cancelar definitivamente el pedido #{confirmId}?
              La API validará el estado PENDING y gestionará las reservas
              en una única transacción. No es posible confirmar pagos aquí.</p>
            <div className="admin-actions">
              <button className="admin-primary" type="button" disabled={busy}
                onClick={()=>void cancelOrder()}>Confirmar cancelación</button>
              <button className="admin-secondary" type="button" disabled={busy}
                onClick={()=>setConfirmId(null)}>Conservar pedido</button>
            </div>
          </div>}
          <h3>Historial de estados</h3>
          {current.history.length===0?<p>Sin eventos disponibles.</p>:(
            <ol className="admin-audit-list">
              {current.history.map(entry=>(
                <li key={entry.id}>
                  <strong>{entry.fromStatus??"INICIO"} → {entry.toStatus}</strong>
                  <span> · {entry.actorType}
                    {entry.actorId!==null?" #"+entry.actorId:""} · {entry.reason}</span>
                  <small><DateText value={entry.changedAt}/></small>
                </li>
              ))}
            </ol>
          )}
        </>}
      </section>}
    </div>
  );
}
