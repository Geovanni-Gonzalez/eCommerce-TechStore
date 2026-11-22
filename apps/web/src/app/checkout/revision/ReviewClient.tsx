"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import { getCart, CartHttpError } from "@/lib/cart-api";
import { formatCatalogCrc } from "@/lib/catalog";
import { validateShippingAddress } from "@/lib/shipping-address";
import {
  CheckoutHttpError, createCheckoutOrder, fetchCheckoutQuote, startCheckoutPayment,
} from "@/lib/checkout-api";
import {
  checkoutAmountsChanged, type CheckoutQuote, type CreatedOrder,
} from "@/lib/checkout-ui";
import type { Cart } from "@/lib/cart";

type Loaded = {
  token: string;
  cart?: Cart;
  quote?: CheckoutQuote;
  error?: string;
};
type Phase = "review" | "creating" | "created" | "uncertain";

function PriceBreakdown({
  subtotalCrc, shippingCrc, taxCrc, totalCrc,
}: {
  subtotalCrc:number; shippingCrc:number; taxCrc:number; totalCrc:number;
}) {
  return (
    <dl className="review-totals">
      <div><dt>Subtotal</dt><dd>{formatCatalogCrc(subtotalCrc)}</dd></div>
      <div><dt>Envío</dt><dd>{formatCatalogCrc(shippingCrc)}</dd></div>
      <div><dt>Impuestos estimados por el servidor</dt>
        <dd>{formatCatalogCrc(taxCrc)}</dd></div>
      <div className="review-totals__grand"><dt>Total</dt>
        <dd>{formatCatalogCrc(totalCrc)}</dd></div>
    </dl>
  );
}

export default function ReviewClient() {
  const {user,deliveryDraft,getToken} = useAuth();
  const token = user?.role === "CLIENT" ? getToken() : null;
  const validAddress = deliveryDraft
    && Object.keys(validateShippingAddress(deliveryDraft)).length === 0;

  const [loaded,setLoaded] = useState<Loaded | null>(null);
  const [retry,setRetry] = useState(0);
  const [phase,setPhase] = useState<Phase>("review");
  const [order,setOrder] = useState<CreatedOrder | null>(null);
  const [message,setMessage] = useState("");
  const [paying,setPaying] = useState(false);
  const requestRef = useRef(0);
  const mutationRef = useRef(false);
  const paymentRef = useRef<{orderId:number;key:string} | null>(null);

  useEffect(() => {
    if (!token || !validAddress || phase !== "review") return;
    const controller = new AbortController();
    const request = ++requestRef.current;
    void getCart(token,controller.signal)
      .then(async cart => ({
        cart,
        quote:await fetchCheckoutQuote(token,cart,controller.signal),
      }))
      .then(({cart,quote}) => {
        if (!controller.signal.aborted && requestRef.current === request) {
          setLoaded({token,cart,quote});
        }
      })
      .catch((cause:unknown) => {
        if (!controller.signal.aborted && requestRef.current === request) {
          const text = cause instanceof Error ? cause.message
            : "No fue posible obtener el resumen del servidor.";
          setLoaded({token,error:text});
        }
      });
    return () => {
      controller.abort();
      requestRef.current = request + 1;
    };
  },[token,user?.id,validAddress,retry,phase]);

  function refreshQuote() {
    if (phase !== "review") return;
    setLoaded(null);
    setMessage("");
    setRetry(v => v + 1);
  }

  async function confirm() {
    if (mutationRef.current || !token || !deliveryDraft
        || !validAddress || phase !== "review"
        || loaded?.token !== token || !loaded.quote || !loaded.cart) return;
    mutationRef.current = true;
    const currentToken = token;
    const address = deliveryDraft;
    setPhase("creating");
    setMessage("");
    try {
      // Una ÚNICA llamada POST. Si la respuesta es ambigua no se reintenta
      // automáticamente: la transacción pudo ya consumir el carrito.
      const created = await createCheckoutOrder(currentToken,address);
      if (getToken() !== currentToken) return;
      setOrder(created);
      paymentRef.current = {orderId:created.id,key:crypto.randomUUID()};
      setPhase("created");
    } catch(cause:unknown) {
      if(getToken() !== currentToken) return;
      if (cause instanceof CheckoutHttpError
          && [400,401,403,404,409,422].includes(cause.status)) {
        setPhase("review");
        setMessage(cause.message);
      } else {
        setPhase("uncertain");
        setMessage("No se pudo determinar si el pedido se creó. "
          + "Por seguridad no repetiremos la confirmación: consulta Mis pedidos.");
      }
    } finally {
      mutationRef.current = false;
    }
  }

  async function startPayment() {
    if (mutationRef.current || paying || !order || !token
        || phase !== "created" || !paymentRef.current
        || paymentRef.current.orderId !== order.id) return;
    mutationRef.current = true;
    const currentToken = token;
    setPaying(true);
    setMessage("");
    try {
      const session = await startCheckoutPayment(
        currentToken,order,paymentRef.current.key,window.location.origin,
      );
      if (getToken() === currentToken) {
        // Solo la API emite el fragmento opaco; esta página no lo imprime.
        window.location.assign(session.redirectUrl);
      }
    } catch(cause:unknown) {
      if (getToken() !== currentToken) return;
      setMessage(cause instanceof CheckoutHttpError && cause.status === 409
        ? "No se pudo iniciar el sandbox. La pasarela puede estar deshabilitada "
          + "o el pedido ya no permite un nuevo intento. Consulta Mis pedidos."
        : cause instanceof Error ? cause.message : "No se pudo iniciar el sandbox.");
    } finally {
      mutationRef.current = false;
      setPaying(false);
    }
  }

  if (!user) return (
    <section className="review-panel">
      <h1>Inicia sesión para revisar el pedido</h1>
      <p>El resumen requiere una sesión de cliente.</p>
      <Link href="/login">Iniciar sesión</Link>
    </section>
  );
  if (user.role !== "CLIENT") return (
    <section className="review-panel">
      <h1>Checkout solo para clientes</h1>
      <p>Esta operación no está disponible para administradores.</p>
      <Link href="/productos">Volver al catálogo</Link>
    </section>
  );
  if (!validAddress || !deliveryDraft) return (
    <section className="review-panel">
      <h1>Prepara primero tu dirección</h1>
      <p>Necesitamos una dirección de entrega válida antes de cotizar el pedido.</p>
      <Link href="/checkout/entrega">Completar dirección</Link>
    </section>
  );

  const current = loaded?.token === token ? loaded : null;
  const currentQuote = current?.quote;
  const changed = Boolean(order && currentQuote
    && checkoutAmountsChanged(currentQuote,order));

  return (
    <section className="review-page" aria-labelledby="review-heading">
      <header className="review-intro">
        <p className="eyebrow">Compra · Paso 2 de 3</p>
        <h1 id="review-heading">Revisar y confirmar pedido</h1>
        <p>Revisa el resumen antes de reservar existencias.
          El pago será una simulación independiente.</p>
      </header>
      <div className="review-layout">
        <div className="review-main">
          <section className="review-panel" aria-labelledby="review-address-title">
            <div className="review-heading-row">
              <h2 id="review-address-title">Entrega</h2>
              {phase === "review" && <Link href="/checkout/entrega">Editar dirección</Link>}
            </div>
            <p><strong>{deliveryDraft.recipientName}</strong> · {deliveryDraft.phone}</p>
            <p>{deliveryDraft.province}, {deliveryDraft.canton}, {deliveryDraft.district}</p>
            <p className="review-break">{deliveryDraft.details}</p>
          </section>

          {phase === "review" && !current && <p className="review-panel"
            role="status">Consultando carrito, impuestos y envío en la API…</p>}
          {phase === "review" && current?.error && (
            <div className="review-panel" role="alert">
              <h2>No se pudo obtener la cotización</h2>
              <p>{current.error}</p>
              <p>La dirección preparada sigue disponible para editar.</p>
              <button type="button" className="review-secondary"
                onClick={refreshQuote}>Reintentar cotización</button>
              <p><Link href="/carrito">Revisar carrito</Link></p>
            </div>
          )}
          {phase === "review" && current?.cart && currentQuote && (
            <section className="review-panel" aria-labelledby="review-items-title">
              <div className="review-heading-row">
                <h2 id="review-items-title">Productos</h2>
                <Link href="/carrito">Editar carrito</Link>
              </div>
              <ul className="review-lines">
                {current.cart.items.map(item => (
                  <li key={item.productId}>
                    <span>{item.name} · {item.quantity} ud.</span>
                    <strong>{formatCatalogCrc(item.lineSubtotalCrc)}</strong>
                  </li>
                ))}
              </ul>
              <p className="review-muted">Unidades: {currentQuote.totalItems}.
                Precios y existencias consultados al servidor.</p>
            </section>
          )}
          {(phase === "created" && order) && (
            <section className="review-panel" aria-labelledby="review-created-title">
              <h2 id="review-created-title">Pedido #{order.id} creado</h2>
              <p role="status" className="review-success">
                Pedido en estado PENDING con reserva de inventario.
                El pago todavía no está confirmado.
              </p>
              <ul className="review-lines">
                {order.items.map(item => (
                  <li key={item.productId}>
                    <span>{item.name} · {item.quantity} ud.</span>
                    <strong>{formatCatalogCrc(item.lineTotalCrc)}</strong>
                  </li>
                ))}
              </ul>
              {changed && <p role="alert" className="review-warning">
                Los importes definitivos cambiaron respecto a la cotización.
                Revisa el nuevo total antes de continuar al pago simulado.
              </p>}
            </section>
          )}
          {phase === "uncertain" && (
            <div className="review-panel" role="alert">
              <h2>Confirmación sin respuesta verificable</h2>
              <p>{message}</p>
              <p>La dirección no se ha borrado. No pulse otra vez confirmar
                hasta revisar si el pedido aparece en el historial.</p>
              <Link href="/pedidos">Consultar Mis pedidos</Link>
            </div>
          )}
        </div>

        <aside className="review-panel review-summary" aria-labelledby="review-total-title">
          <h2 id="review-total-title">Resumen en colones</h2>
          {order ? (
            <>
              <PriceBreakdown {...order.totals} />
              <p className="review-muted">Importes definitivos del pedido #{order.id},
                calculados transaccionalmente por la API.</p>
            </>
          ) : currentQuote ? (
            <>
              <PriceBreakdown {...currentQuote.totals} />
              <p className="review-muted">Cotización provisional ({currentQuote.shippingPolicy}).
                El backend recalcula los importes al confirmar y puede
                variar el precio o el stock.</p>
            </>
          ) : <p className="review-muted">La cotización todavía no está disponible.</p>}

          {message && phase !== "uncertain" && <p className="review-warning"
            role="alert">{message}</p>}
          {phase === "review" && currentQuote && (
            <>
              <button type="button" className="review-primary" disabled={mutationRef.current}
                onClick={() => void confirm()}>
                Confirmar pedido y reservar existencias
              </button>
              <button type="button" className="review-secondary"
                onClick={refreshQuote}>Actualizar cotización</button>
            </>
          )}
          {phase === "creating" && <p role="status">
            Creando el pedido y reservando stock de forma transaccional…
          </p>}
          {phase === "created" && order && (
            <>
              <p className="review-muted">Paso 3 de 3: únicamente demostración.
                No se introducen tarjetas ni se transfiere dinero.</p>
              <button className="review-primary" type="button" disabled={paying}
                onClick={() => void startPayment()}>
                {paying ? "Preparando sandbox…" : "Continuar a pago simulado"}
              </button>
              <p className="review-muted">
                La pasarela debe estar habilitada en el servidor.
                Se abrirá una página de prueba y deberás volver a identificarte
                para consultar el resultado verificado.
              </p>
              <Link href="/pedidos">Ver historial de pedidos</Link>
            </>
          )}
          {phase === "review" && <Link href="/checkout/entrega">Volver a entrega</Link>}
          {phase === "review" && <Link href="/carrito">Volver al carrito</Link>}
        </aside>
      </div>
    </section>
  );
}
