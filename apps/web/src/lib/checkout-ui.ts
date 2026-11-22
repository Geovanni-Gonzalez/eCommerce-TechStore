/**
 * BKL-034: Validadores defensivos para cotizaciones, snapshots de pedido y
 * redirecciones de pago sandbox. El backend decide dinero, stock y estado.
 */
import { SHIPPING_FIELDS, type ShippingAddress } from "./shipping-address.ts";
import type { Cart } from "./cart.ts";

type Shape = Record<string, unknown>;
const object = (v: unknown): Shape | null =>
  v && typeof v === "object" && !Array.isArray(v) ? v as Shape : null;
const positive = (v: unknown): v is number =>
  typeof v === "number" && Number.isSafeInteger(v) && v > 0;
const nonEmpty = (v: unknown): v is string =>
  typeof v === "string" && v.trim().length > 0;
const cents = (v: unknown): number | null => {
  if (typeof v !== "number" || !Number.isFinite(v) || v < 0) return null;
  const c = Math.round(v * 100);
  return Number.isSafeInteger(c) && Math.abs(v * 100 - c) < 0.00001 ? c : null;
};
export type CheckoutTotals = {
  subtotalCrc: number;
  shippingCrc: number;
  taxCrc: number;
  totalCrc: number;
};
export type CheckoutQuote = {
  totalItems: number;
  totals: CheckoutTotals;
  shippingPolicy: "FIXED" | "FREE_OVER_THRESHOLD";
  estimateOnly: true;
};
export type CreatedOrder = {
  id: number; status: "PENDING"; paymentMode: "SIMULATED";
  items: Array<{
    productId: number; name: string; quantity: number;
    unitPriceCrc: number; lineTotalCrc: number;
  }>;
  shippingAddress: ShippingAddress;
  totals: CheckoutTotals;
  createdAt: string;
};
export type SandboxSession = {
  attemptId: number; orderId: number; totalCrc: number;
  currency: "CRC"; expiresAt: string; redirectUrl: string;
};

export function parseCheckoutTotals(input: unknown): CheckoutTotals {
  const data = object(input);
  if (!data) throw new Error("El servidor devolvió totales no válidos.");
  const parts = ["subtotalCrc","shippingCrc","taxCrc","totalCrc"] as const;
  const values = parts.map(key => cents(data[key]));
  if (values.some(v => v === null)
      || values[0]! + values[1]! + values[2]! !== values[3]) {
    throw new Error("Los importes de la API no coinciden.");
  }
  return {
    subtotalCrc: data.subtotalCrc as number,
    shippingCrc: data.shippingCrc as number,
    taxCrc: data.taxCrc as number,
    totalCrc: data.totalCrc as number,
  };
}

export function parseCheckoutQuote(input: unknown, cart: Cart): CheckoutQuote {
  const data = object(input);
  if (!data || !positive(data.totalItems) || data.estimateOnly !== true
      || !["FIXED","FREE_OVER_THRESHOLD"].includes(String(data.shippingPolicy))
      || data.totalItems !== cart.totalItems || !cart.checkoutReady) {
    throw new Error("La cotización no corresponde al carrito disponible.");
  }
  const totals = parseCheckoutTotals(data.totals);
  if (cents(totals.subtotalCrc) !== cents(cart.subtotalCrc)) {
    throw new Error("Los precios del carrito cambiaron; actualiza antes de continuar.");
  }
  return {
    totalItems: data.totalItems, totals,
    shippingPolicy: data.shippingPolicy as CheckoutQuote["shippingPolicy"],
    estimateOnly: true,
  };
}

export function parseCreatedOrder(input: unknown, address: ShippingAddress): CreatedOrder {
  const data = object(input);
  const sentAddress = object(data?.shippingAddress);
  if (!data || !positive(data.id) || data.status !== "PENDING"
      || data.paymentMode !== "SIMULATED" || !Array.isArray(data.items)
      || data.items.length === 0 || !nonEmpty(data.createdAt)
      || !Number.isFinite(Date.parse(data.createdAt))
      || !sentAddress || SHIPPING_FIELDS.some(f => sentAddress[f] !== address[f])) {
    throw new Error("El servidor no devolvió el pedido esperado; comprueba Mis pedidos.");
  }
  const items: CreatedOrder["items"] = data.items.map((item: unknown) => {
    const row = object(item);
    if (!row || !positive(row.productId) || !nonEmpty(row.name)
        || !positive(row.quantity) || cents(row.unitPriceCrc) === null
        || cents(row.lineTotalCrc) === null
        || cents(row.unitPriceCrc)! * row.quantity !== cents(row.lineTotalCrc)) {
      throw new Error("El pedido devolvió una línea inesperada.");
    }
    return {
      productId: row.productId, name: row.name, quantity: row.quantity,
      unitPriceCrc: row.unitPriceCrc as number,
      lineTotalCrc: row.lineTotalCrc as number,
    };
  });
  const totals = parseCheckoutTotals(data.totals);
  const sum = items.reduce((n, row) => n + cents(row.lineTotalCrc)!, 0);
  if (!Number.isSafeInteger(sum) || sum !== cents(totals.subtotalCrc)) {
    throw new Error("El pedido contiene importes inconsistentes.");
  }
  return {
    id: data.id, status: "PENDING", paymentMode: "SIMULATED", items,
    shippingAddress: address, totals, createdAt: data.createdAt,
  };
}

/**
 * El único destino permitido es la ruta fija de pago sandbox del mismo
 * origen que la aplicación, con token 256 bits en fragmento, nunca query.
 * No registrar ni renderizar la URL: contiene un secreto de un solo uso.
 */
export function parseSandboxSession(
  input: unknown, order: CreatedOrder, appOrigin: string,
): SandboxSession {
  const data = object(input);
  const price = cents(data?.totalCrc);
  if (!data || !positive(data.attemptId) || data.orderId !== order.id
      || data.currency !== "CRC" || price === null
      || price !== cents(order.totals.totalCrc)
      || !nonEmpty(data.expiresAt)
      || !Number.isFinite(Date.parse(data.expiresAt))
      || !nonEmpty(data.redirectUrl)) {
    throw new Error("El servidor devolvió una sesión de pago inesperada.");
  }
  let url: URL;
  try {
    url = new URL(data.redirectUrl);
    const expected = new URL(appOrigin);
    if ((expected.protocol !== "https:" && !(expected.protocol === "http:"
      && ["localhost","127.0.0.1","[::1]"].includes(expected.hostname)))
      || url.origin !== expected.origin
      || url.pathname !== "/pagos/sandbox" || url.search !== ""
      || url.username !== "" || url.password !== ""
      || !/^#session=[A-Za-z0-9_-]{43}$/.test(url.hash)) {
      throw new Error("Destino no autorizado.");
    }
  } catch {
    throw new Error("La API devolvió una redirección de pago no autorizada.");
  }
  return {
    attemptId: data.attemptId, orderId: data.orderId,
    totalCrc: data.totalCrc as number, currency:"CRC",
    expiresAt: data.expiresAt, redirectUrl: url.href,
  };
}

export function checkoutAmountsChanged(
  quote: CheckoutQuote, order: CreatedOrder,
): boolean {
  return cents(quote.totals.totalCrc) !== cents(order.totals.totalCrc)
    || cents(quote.totals.subtotalCrc) !== cents(order.totals.subtotalCrc)
    || cents(quote.totals.shippingCrc) !== cents(order.totals.shippingCrc)
    || cents(quote.totals.taxCrc) !== cents(order.totals.taxCrc);
}
