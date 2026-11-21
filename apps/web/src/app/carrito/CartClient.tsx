"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import { type Cart, parseCartQuantity } from "@/lib/cart";
import {
  CartHttpError, clearCart, getCart, removeCartItem, updateCartItem,
} from "@/lib/cart-api";
import { formatCatalogCrc } from "@/lib/catalog";

export default function CartClient() {
  const { user, getToken, logout } = useAuth();
  const token = user?.role === "CLIENT" ? getToken() : null;
  const [cart, setCart] = useState<Cart | null>(null);
  const [loadedToken, setLoadedToken] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [confirmClear, setConfirmClear] = useState(false);
  const sessionRef = useRef<string | null>(null);
  const mutationRef = useRef(false);
  const requestRef = useRef(0);

  useEffect(() => {
    sessionRef.current = token;
    const request = ++requestRef.current;
    const controller = new AbortController();
    if (!token) return () => controller.abort();
    void getCart(token, controller.signal).then(data => {
      if (controller.signal.aborted || request !== requestRef.current
          || sessionRef.current !== token) return;
      setCart(data);
      setLoadedToken(token);
      setDrafts({});
      setNotice("");
      setError("");
      setConfirmClear(false);
      setLoading(false);
    }).catch((cause: unknown) => {
      if (controller.signal.aborted || request !== requestRef.current
          || sessionRef.current !== token) return;
      setCart(null);
      setLoadedToken(token);
      setLoading(false);
      if (cause instanceof CartHttpError && cause.status === 401) {
        void logout();
      } else {
        setError(cause instanceof Error ? cause.message : "No se pudo cargar el carrito.");
      }
    });
    return () => {
      controller.abort();
      requestRef.current = request + 1;
      sessionRef.current = null;
    };
  }, [token, user?.id, logout]); // Un cambio de cuenta invalida todo dato privado.

  async function refresh() {
    if (!token || mutationRef.current) return;
    const currentToken = token;
    setLoading(true);
    setError("");
    try {
      const data = await getCart(currentToken);
      if (sessionRef.current !== currentToken) return;
      setCart(data);
      setDrafts({});
      setNotice("");
    } catch (cause: unknown) {
      if (sessionRef.current !== currentToken) return;
      setError(cause instanceof Error ? cause.message : "No se pudo actualizar el carrito.");
      if (cause instanceof CartHttpError && cause.status === 401) void logout();
    } finally {
      if (sessionRef.current === currentToken) setLoading(false);
    }
  }

  async function runMutation(
    task: (activeToken: string) => Promise<Cart | void>,
    success: string,
  ) {
    if (!token || mutationRef.current) return;
    mutationRef.current = true;
    const currentToken = token;
    setBusy(true);
    setError("");
    setNotice("");
    setConfirmClear(false);
    try {
      const result = await task(currentToken);
      if (sessionRef.current !== currentToken) return;
      const updated = result ?? await getCart(currentToken);
      if (sessionRef.current !== currentToken) return;
      setCart(updated);
      setDrafts({});
      setNotice(success);
    } catch (cause: unknown) {
      if (sessionRef.current !== currentToken) return;
      setError(cause instanceof Error ? cause.message : "No se pudo modificar el carrito.");
      if (cause instanceof CartHttpError && cause.status === 401) {
        void logout();
      } else if (cause instanceof CartHttpError && [404, 409].includes(cause.status)) {
        try {
          const updated = await getCart(currentToken);
          if (sessionRef.current === currentToken) {
            setCart(updated);
            setDrafts({});
          }
        } catch {
          // Mantener mensaje de error original y no perder datos anteriores.
        }
      }
    } finally {
      mutationRef.current = false;
      if (sessionRef.current === currentToken) setBusy(false);
    }
  }

  const fetchingForToken = Boolean(token && loadedToken !== token);
  const currentCart = token && loadedToken === token && !loading ? cart : null;
  return (
    <section className="cart-page" aria-labelledby="cart-heading">
      <header className="cart-intro">
        <p className="eyebrow">Tu selección · TechStore</p>
        <h1 id="cart-heading">Mi carrito</h1>
        <p>Modifica cantidades y revisa el subtotal calculado por el servidor.</p>
      </header>
      {!user ? (
        <div className="cart-panel">
          <h2>Inicia sesión para consultar tu carrito</h2>
          <p>Tu carrito se guarda en PostgreSQL y se asocia a tu cuenta.</p>
          <Link href="/login" className="button-link">Iniciar sesión</Link>
        </div>
      ) : user.role !== "CLIENT" ? (
        <div className="cart-panel" role="alert">
          <h2>Área exclusiva de clientes</h2>
          <p>Las cuentas de administración no tienen carrito de compra.</p>
          <Link href="/productos">Volver al catálogo</Link>
        </div>
      ) : (
        <>
          {(loading || fetchingForToken) && <p className="cart-panel" role="status">Consultando tu carrito…</p>}
          {loadedToken === token && error && <p className="cart-error" role="alert">{error}</p>}
          {loadedToken === token && notice && <p className="cart-success" role="status">{notice}</p>}
          {!loading && !fetchingForToken && !cart && (
            <div className="cart-panel">
              <h2>No se pudo mostrar el carrito</h2>
              <button type="button" className="cart-secondary"
                onClick={() => void refresh()}>Reintentar</button>
            </div>
          )}
          {currentCart && currentCart.items.length === 0 && (
            <div className="cart-panel">
              <h2>Tu carrito está vacío</h2>
              <p>Explora los productos y agrega los accesorios que necesitas.</p>
              <Link href="/productos" className="button-link">Ver productos</Link>
            </div>
          )}
          {currentCart && currentCart.items.length > 0 && (
            <div className="cart-layout">
              <div>
                <div className="cart-toolbar">
                  <h2>Productos seleccionados ({currentCart.totalItems})</h2>
                  <button type="button" className="cart-text-button" disabled={busy}
                    onClick={() => void refresh()}>Actualizar lista</button>
                </div>
                <ul className="cart-items">
                  {currentCart.items.map(item => (
                    <li key={item.productId} className="cart-line">
                      <div className="cart-line__details">
                        <h3><Link href={`/productos/${item.productId}`}>{item.name}</Link></h3>
                        <p className="cart-muted">Referencia: {item.sku}</p>
                        <p>Precio unitario: <strong>{formatCatalogCrc(item.unitPriceCrc)}</strong></p>
                        <p className={item.available ? "cart-stock-ok" : "cart-stock-error"}>
                          {item.available
                            ? `Disponible · ${item.stock} en existencias`
                            : `Producto sin disponibilidad suficiente · stock actual: ${item.stock}`}
                        </p>
                      </div>
                      <div className="cart-line__actions">
                        <form onSubmit={event => {
                          event.preventDefault();
                          const qty = parseCartQuantity(drafts[item.productId] ?? String(item.quantity));
                          if (qty === null) {
                            setError("La cantidad debe ser un entero entre 1 y 99.");
                            return;
                          }
                          void runMutation(
                            t => updateCartItem(t, item.productId, qty),
                            "Cantidad actualizada correctamente.",
                          );
                        }}>
                          <label htmlFor={`cart-qty-${item.productId}`}>Cantidad de {item.name}</label>
                          <div className="cart-line__qty">
                            <input id={`cart-qty-${item.productId}`} type="number"
                              min={1} max={99} step={1} inputMode="numeric"
                              value={drafts[item.productId] ?? String(item.quantity)}
                              onChange={event => setDrafts(previous => ({
                                ...previous, [item.productId]: event.target.value,
                              }))}
                              disabled={busy} required />
                            <button type="submit" className="cart-secondary" disabled={busy}>
                              Actualizar {item.name}
                            </button>
                          </div>
                        </form>
                        <p className="cart-line__subtotal">
                          Importe: <strong>{formatCatalogCrc(item.lineSubtotalCrc)}</strong>
                        </p>
                        <button type="button" className="cart-text-button"
                          disabled={busy} onClick={() => void runMutation(
                            async t => { await removeCartItem(t, item.productId); },
                            "Producto retirado del carrito.",
                          )}>Quitar {item.name}</button>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
              <aside className="cart-summary" aria-labelledby="cart-summary-heading">
                <h2 id="cart-summary-heading">Resumen del carrito</h2>
                <div className="cart-summary__row">
                  <span>Artículos</span><strong>{currentCart.totalItems}</strong>
                </div>
                <div className="cart-summary__row cart-summary__total">
                  <span>Subtotal estimado</span>
                  <strong>{formatCatalogCrc(currentCart.subtotalCrc)}</strong>
                </div>
                <p className="cart-muted">Los precios y el subtotal provienen de la API.
                  No incluyen envío ni impuestos; pueden cambiar al confirmar el pedido.</p>
                {!currentCart.checkoutReady && (
                  <p className="cart-stock-error" role="status">
                    Revisa los artículos sin disponibilidad antes de continuar.
                  </p>
                )}
                <p className="cart-muted">La dirección de entrega y el checkout visual
                  estarán disponibles en BKL-031 y BKL-034. Aquí no se cobra dinero.</p>
                <Link href="/productos" className="cart-secondary cart-summary__link">
                  Seguir comprando
                </Link>
                {!confirmClear ? (
                  <button type="button" className="cart-text-button"
                    disabled={busy} onClick={() => setConfirmClear(true)}>
                    Vaciar carrito
                  </button>
                ) : (
                  <div className="cart-clear">
                    <p>¿Quitar todos los artículos?</p>
                    <button type="button" className="cart-secondary" disabled={busy}
                      onClick={() => void runMutation(async t => { await clearCart(t); },
                        "El carrito quedó vacío.")}>Confirmar vaciado</button>
                    <button type="button" className="cart-text-button" disabled={busy}
                      onClick={() => setConfirmClear(false)}>Cancelar</button>
                  </div>
                )}
              </aside>
            </div>
          )}
        </>
      )}
    </section>
  );
}
