"use client";

import Link from "next/link";
import ShippingTrackingPanel from "./ShippingTrackingPanel";
import OrderNotificationsPanel from "./OrderNotificationsPanel";
import { useCallback, useRef, useState } from "react";
import { isSafeApiOrigin } from "@/lib/payment-result";
import {
  ACTOR_COPY, REASON_COPY, STATUS_COPY, formatCrc, formatOrderDate,
  isOrderId, parseOrderDetail, parseOrderHistory, parseOrderPage,
  type OrderDetail, type OrderHistoryEntry, type OrderPage, type OrderSummary,
  type OrderStatus,
} from "@/lib/order-history";

const API_BASE = (
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");
const PAGE_SIZE = 12;

class OrderReadError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "OrderReadError";
  }
}

function responseMessage(status: number): string {
  if (status === 401) return "La sesión expiró. Vuelve a verificar tu identidad.";
  if (status === 403) return "Esta cuenta no puede consultar pedidos de cliente.";
  if (status === 404) {
    return "El pedido no existe o no pertenece a esta cuenta.";
  }
  return "El servidor no pudo completar la consulta (HTTP " + status + ").";
}

async function jsonResponse(response: Response): Promise<unknown> {
  if (!response.ok) {
    throw new OrderReadError(response.status, responseMessage(response.status));
  }
  return (await response.json()) as unknown;
}

async function authGet(path: string, token: string): Promise<unknown> {
  if (!isSafeApiOrigin(API_BASE)) {
    throw new Error("API insegura: se requiere HTTPS (HTTP solo en localhost).");
  }
  const response = await fetch(API_BASE + path, {
    method: "GET",
    headers: { Authorization: "Bearer " + token, Accept: "application/json" },
    cache: "no-store",
    credentials: "omit",
    referrerPolicy: "no-referrer",
  });
  return jsonResponse(response);
}

async function remoteLogout(token: string): Promise<void> {
  if (!isSafeApiOrigin(API_BASE)) return;
  try {
    await fetch(API_BASE + "/api/auth/logout", {
      method: "POST",
      headers: { Authorization: "Bearer " + token },
      cache: "no-store", credentials: "omit", referrerPolicy: "no-referrer",
    });
  } catch {
    // Revocación best-effort; el token ya fue borrado de memoria.
  }
}

function StatusTag({ status }: { status: OrderStatus }) {
  return (
    <span className={"orders-status orders-status--" + status.toLowerCase()}>
      {STATUS_COPY[status].title}
    </span>
  );
}

function DetailView({
  order, history, getToken,
}: {
  order: OrderDetail; history: OrderHistoryEntry[];
  getToken:()=>string|null;
}) {
  const address = order.shippingAddress;
  return (
    <article className="orders-detail" aria-labelledby="order-detail-heading">
      <div className="orders-detail__head">
        <div>
          <p className="eyebrow">Detalle histórico</p>
          <h2 id="order-detail-heading">Pedido #{order.id}</h2>
        </div>
        <StatusTag status={order.status} />
      </div>
      <p>{STATUS_COPY[order.status].description}</p>
      <p className="orders-muted">Creado: {formatOrderDate(order.createdAt)}</p>

      <h3>Productos del pedido</h3>
      <div className="orders-table-scroll">
        <table className="orders-table">
          <thead><tr>
            <th scope="col">Producto</th>
            <th scope="col">Cantidad</th>
            <th scope="col">Precio unitario</th>
            <th scope="col">Subtotal</th>
          </tr></thead>
          <tbody>
            {order.items.map((item, i) => (
              <tr key={item.productId + "-" + i}>
                <th scope="row">{item.name}</th>
                <td>{item.quantity}</td>
                <td>{formatCrc(item.unitPriceCrc)}</td>
                <td>{formatCrc(item.lineTotalCrc)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3>Resumen del pedido</h3>
      <dl className="orders-totals">
        <div><dt>Subtotal</dt><dd>{formatCrc(order.totals.subtotalCrc)}</dd></div>
        <div><dt>Envío</dt><dd>{formatCrc(order.totals.shippingCrc)}</dd></div>
        <div><dt>Impuestos</dt><dd>{formatCrc(order.totals.taxCrc)}</dd></div>
        <div className="orders-totals__total">
          <dt>Total</dt><dd>{formatCrc(order.totals.totalCrc)}</dd>
        </div>
      </dl>

      <h3>Dirección de entrega registrada</h3>
      <address className="orders-address">
        <strong>{address.recipientName}</strong>
        <span>{address.province}, {address.canton}, {address.district}</span>
        <span>{address.details}</span>
        <span>Teléfono: {address.phone}</span>
      </address>

      <OrderNotificationsPanel orderId={order.id} getToken={getToken} />
      <ShippingTrackingPanel orderId={order.id} getToken={getToken} />
      <h3>Historial de estados</h3>
      {history.length === 0 ? (
        <p className="orders-muted">
          Todavía no existen cambios de estado registrados en este pedido.
        </p>
      ) : (
        <ol className="orders-timeline" aria-label="Historial de estados del pedido">
          {history.map((entry, i) => (
            <li key={entry.changedAt + "-" + i}>
              <div className="orders-timeline__item">
                <StatusTag status={entry.toStatus} />
                <strong>{REASON_COPY[entry.reason]}</strong>
                <span className="orders-muted">
                  {formatOrderDate(entry.changedAt)} · {ACTOR_COPY[entry.actorType]}
                </span>
                {entry.fromStatus && (
                  <span className="orders-muted">
                    Desde {STATUS_COPY[entry.fromStatus].title.toLowerCase()}
                  </span>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
      <p className="orders-footnote">
        El historial refleja estados del pedido y operaciones de sandbox.
        No acredita pagos ni reembolsos bancarios reales.
      </p>
    </article>
  );
}

type Phase = "access" | "authenticating" | "ready";

export default function OrdersClient() {
  const [phase, setPhase] = useState<Phase>("access");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [listError, setListError] = useState("");
  const [detailError, setDetailError] = useState("");
  const [listBusy, setListBusy] = useState(false);
  const [detailBusy, setDetailBusy] = useState(false);
  const [page, setPage] = useState<OrderPage>({
    items: [], hasMore: false, nextBeforeId: null,
  });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<OrderDetail | null>(null);
  const [timeline, setTimeline] = useState<OrderHistoryEntry[]>([]);

  // Token exclusivamente en memoria. Nunca pasar a URL, cookies o storage.
  const session = useRef<string | null>(null);
  // La lectura del Bearer ocurre solo en efectos/handlers, nunca en render.
  const getSessionToken = useCallback(() => session.current, []);
  const generation = useRef(0);
  const detailRequest = useRef(0);
  const activeListRequest = useRef(0);

  function resetPrivateView() {
    activeListRequest.current++;
    detailRequest.current++;
    setPage({ items: [], hasMore: false, nextBeforeId: null });
    setSelectedId(null);
    setDetail(null);
    setTimeline([]);
    setListBusy(false);
    setDetailBusy(false);
    setDetailError("");
    setListError("");
  }

  function invalidateSession(message: string) {
    // Respuestas antiguas no podrán repoblar la UI tras un 401 o logout.
    generation.current++;
    session.current = null;
    resetPrivateView();
    setPassword("");
    setEmail("");
    setAuthError(message);
    setPhase("access");
  }

  async function fetchPage(token: string, cursor: number | null, append: boolean) {
    const version = generation.current;
    const request = ++activeListRequest.current;
    setListBusy(true);
    setListError("");
    try {
      const path = "/api/orders?limit=" + PAGE_SIZE
        + (cursor === null ? "" : "&beforeId=" + cursor);
      const result = parseOrderPage(await authGet(path, token));
      if (generation.current !== version
          || session.current !== token || activeListRequest.current !== request) return;
      setPage(previous => {
        if (!append) return result;
        const ids = new Set(previous.items.map(o => o.id));
        return {
          ...result,
          items: [...previous.items,
            ...result.items.filter(o => !ids.has(o.id))],
        };
      });
    } catch (failure: unknown) {
      if (generation.current !== version || activeListRequest.current !== request) return;
      if (failure instanceof OrderReadError && [401, 403].includes(failure.status)) {
        invalidateSession(failure.message);
      } else {
        setListError(failure instanceof Error
          ? failure.message : "No se pudo consultar tu historial.");
      }
    } finally {
      if (generation.current === version && activeListRequest.current === request) {
        setListBusy(false);
      }
    }
  }

  async function login() {
    if (phase !== "access") return;
    const version = ++generation.current;
    setPhase("authenticating");
    setAuthError("");
    resetPrivateView();
    let issuedToken: string | null = null;
    try {
      if (!isSafeApiOrigin(API_BASE)) {
        throw new Error("API insegura: se requiere HTTPS (HTTP solo en localhost).");
      }
      const response = await fetch(API_BASE + "/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ email, password }),
        cache: "no-store", credentials: "omit", referrerPolicy: "no-referrer",
      });
      const data = await jsonResponse(response);
      if (!data || typeof data !== "object") {
        throw new Error("La sesión de la API no es válida.");
      }
      const auth = data as { accessToken?: unknown; user?: { role?: unknown } };
      if (typeof auth.accessToken !== "string" || !auth.accessToken) {
        throw new Error("La sesión de la API no es válida.");
      }
      issuedToken = auth.accessToken;
      if (auth.user?.role !== "CLIENT") {
        throw new OrderReadError(403, "Se requiere una cuenta de cliente.");
      }
      if (version !== generation.current) return;
      session.current = issuedToken;
      setPassword("");
      setEmail("");
      setPhase("ready");
      await fetchPage(issuedToken, null, false);
      issuedToken = null;
    } catch (failure: unknown) {
      if (version === generation.current) {
        invalidateSession(failure instanceof Error
          ? failure.message : "No fue posible verificar tu identidad.");
      }
    } finally {
      // Si la sesión no quedó activa (p. ej. ADMIN o navegación tardía), revocarla.
      if (issuedToken && session.current !== issuedToken) {
        await remoteLogout(issuedToken);
      }
    }
  }

  async function closeSession() {
    const token = session.current;
    generation.current++;
    session.current = null;
    resetPrivateView();
    setAuthError("");
    setPassword("");
    setEmail("");
    setPhase("access");
    if (token) await remoteLogout(token);
  }

  async function selectOrder(id: number) {
    const token = session.current;
    if (!token || !isOrderId(id) || phase !== "ready") return;
    const version = generation.current;
    const request = ++detailRequest.current;
    setSelectedId(id);
    setDetail(null);
    setTimeline([]);
    setDetailError("");
    setDetailBusy(true);
    try {
      const [rawDetail, rawHistory] = await Promise.all([
        authGet("/api/orders/" + id, token),
        authGet("/api/orders/" + id + "/history", token),
      ]);
      const found = parseOrderDetail(rawDetail, id);
      const history = parseOrderHistory(rawHistory);
      if (generation.current !== version || session.current !== token
          || detailRequest.current !== request) return;
      setDetail(found);
      setTimeline(history);
    } catch (failure: unknown) {
      if (generation.current !== version || detailRequest.current !== request) return;
      if (failure instanceof OrderReadError && [401, 403].includes(failure.status)) {
        invalidateSession(failure.message);
      } else {
        setDetailError(failure instanceof Error
          ? failure.message : "No se pudo consultar el detalle.");
      }
    } finally {
      if (generation.current === version && detailRequest.current === request) {
        setDetailBusy(false);
      }
    }
  }

  function loadMore() {
    if (!listBusy && session.current && page.hasMore
        && page.nextBeforeId !== null) {
      void fetchPage(session.current, page.nextBeforeId, true);
    }
  }

  function refreshList() {
    if (!listBusy && session.current) {
      // La lista y el detalle se refrescan de forma independiente.
      void fetchPage(session.current, null, false);
      if (selectedId !== null) void selectOrder(selectedId);
    }
  }

  return (
    <section className="orders-page" aria-labelledby="orders-heading">
      <div className="orders-intro">
        <div>
          <p className="eyebrow">Tu cuenta · Historial</p>
          <h1 id="orders-heading">Mis pedidos</h1>
          <p className="lead">
            Consulta el estado, el detalle histórico y los cambios de
            tus pedidos de TechStore.
          </p>
          <p className="orders-muted">
            Los estados de pago son simulaciones; no constituyen recibos
            bancarios ni acreditan cargos reales.
          </p>
        </div>
        <div className="orders-intro__mark" aria-hidden="true">01 / 03</div>
      </div>

      {phase !== "ready" ? (
        <section className="orders-access" aria-labelledby="orders-auth-heading">
          <p className="eyebrow">Acceso privado</p>
          <h2 id="orders-auth-heading">Consulta solo tus compras</h2>
          <p>
            Identifícate con tu cuenta CLIENT. Hasta que BKL-017 integre
            el acceso general, esta consulta utiliza una sesión puntual
            que permanece solo en memoria.
          </p>
          <form className="orders-access__form" onSubmit={event => {
            event.preventDefault();
            void login();
          }}>
            <label htmlFor="orders-email">Correo electrónico</label>
            <input id="orders-email" name="email" type="email" required
              autoComplete="username" value={email}
              disabled={phase === "authenticating"}
              onChange={e => setEmail(e.target.value)} />
            <label htmlFor="orders-password">Contraseña</label>
            <input id="orders-password" name="password" type="password" required
              autoComplete="current-password" value={password}
              disabled={phase === "authenticating"}
              onChange={e => setPassword(e.target.value)} />
            <button className="sandbox-submit" type="submit"
              disabled={phase === "authenticating"}>
              {phase === "authenticating" ? "Verificando acceso…" : "Consultar mis pedidos"}
            </button>
            {phase === "authenticating" && (
              <p role="status">Validando tu identidad con el servidor…</p>
            )}
            {authError && <p className="orders-error" role="alert">{authError}</p>}
          </form>
          <p className="orders-muted">
            No se guarda la contraseña ni el token en la URL o en
            localStorage/sessionStorage.
          </p>
        </section>
      ) : (
        <>
          <div className="orders-actions">
            <span className="orders-session-label">
              <span aria-hidden="true">●</span> Sesión de consulta privada
            </span>
            <div className="orders-actions__buttons">
              <button type="button" className="orders-button orders-button--outline"
                onClick={refreshList} disabled={listBusy}>
                Actualizar
              </button>
              <button type="button" className="orders-button orders-button--quiet"
                onClick={() => void closeSession()}>
                Cerrar sesión
              </button>
            </div>
          </div>

          <div className="orders-layout">
            <section aria-labelledby="orders-list-heading" className="orders-list">
              <div className="orders-section-heading">
                <h2 id="orders-list-heading">Pedidos registrados</h2>
                <span className="orders-muted">Más recientes primero</span>
              </div>
              {listError && <p role="alert" className="orders-error">{listError}</p>}
              {listBusy && <p role="status">Cargando pedidos…</p>}
              {page.items.length === 0 && !listBusy && !listError && (
                <div className="orders-empty">
                  <h3>Aún no hay pedidos</h3>
                  <p>Cuando completes un checkout, tus pedidos aparecerán aquí.</p>
                  <Link href="/productos">Explorar productos</Link>
                </div>
              )}
              {page.items.length > 0 && (
                <ol className="orders-cards">
                  {page.items.map((order: OrderSummary) => (
                    <li key={order.id}>
                      <button
                        type="button"
                        className={"orders-card" + (
                          selectedId === order.id ? " orders-card--active" : ""
                        )}
                        aria-pressed={selectedId === order.id}
                        aria-label={"Consultar detalle del pedido " + order.id}
                        onClick={() => void selectOrder(order.id)}>
                        <span className="orders-card__top">
                          <strong>Pedido #{order.id}</strong>
                          <StatusTag status={order.status} />
                        </span>
                        <span className="orders-card__date">
                          {formatOrderDate(order.createdAt)}
                        </span>
                        <span className="orders-card__bottom">
                          <strong>{formatCrc(order.totals.totalCrc)}</strong>
                          <span className="orders-card__cta">Ver detalle →</span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ol>
              )}
              {page.hasMore && (
                <button type="button"
                  className="orders-button orders-button--outline orders-more"
                  disabled={listBusy} onClick={loadMore}>
                  {listBusy ? "Cargando…" : "Cargar pedidos anteriores"}
                </button>
              )}
              {!page.hasMore && page.items.length > 0 && (
                <p className="orders-end">Has llegado al final de tu historial.</p>
              )}
            </section>

            <section className="orders-detail-container" aria-label="Detalle del pedido">
              {selectedId === null && (
                <div className="orders-placeholder">
                  <div className="orders-placeholder__icon" aria-hidden="true">≡</div>
                  <h2>Selecciona un pedido</h2>
                  <p>Verás sus productos, totales, entrega y cambios de estado.</p>
                </div>
              )}
              {selectedId !== null && detailBusy && (
                <div className="orders-placeholder" role="status">
                  Consultando detalle y movimientos del pedido #{selectedId}…
                </div>
              )}
              {selectedId !== null && !detailBusy && detailError && (
                <div className="orders-placeholder">
                  <p role="alert" className="orders-error">{detailError}</p>
                  <button type="button" className="orders-button orders-button--outline"
                    onClick={() => void selectOrder(selectedId)}>
                    Reintentar
                  </button>
                </div>
              )}
              {selectedId !== null && detail !== null && !detailBusy && (
                <DetailView order={detail} history={timeline}
                  getToken={getSessionToken} />
              )}
            </section>
          </div>
        </>
      )}

      <p className="orders-bottom-link">
        <Link href="/">Volver al inicio de TechStore</Link>
      </p>
    </section>
  );
}
