/**
 * BKL-034: operaciones reales de checkout. Sin userId, precio ni tokens en URL.
 */
import { isSafeApiOrigin } from "./payment-result";
import { makeShippingAddressBody, type ShippingAddress } from "./shipping-address";
import {
  parseCheckoutQuote, parseCreatedOrder, parseSandboxSession,
  type CheckoutQuote, type CreatedOrder, type SandboxSession,
} from "./checkout-ui";
import type { Cart } from "./cart";

const API_BASE = (
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

export class CheckoutHttpError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "CheckoutHttpError";
  }
}
function httpMessage(status: number): string {
  switch (status) {
    case 400: return "Revisa los datos de entrega.";
    case 401: return "Tu sesión ha expirado; vuelve a iniciar sesión.";
    case 403: return "Solo una cuenta de cliente puede confirmar pedidos.";
    case 404: return "El pedido solicitado no está disponible para tu cuenta.";
    case 409: return "El stock o el estado del pedido cambió. Revisa el carrito o el historial.";
    case 422: return "El carrito está vacío o el pedido ya se confirmó.";
    default: return status >= 500
      ? "El servidor no respondió correctamente. Comprueba Mis pedidos antes de repetir una confirmación."
      : "La operación no pudo completarse.";
  }
}
async function request(
  method: "GET" | "POST", path: string, token: string,
  options?: {body?: unknown; idempotencyKey?: string; signal?: AbortSignal},
): Promise<unknown> {
  if (!isSafeApiOrigin(API_BASE)
      || !/^[A-Za-z0-9_-]{32,}$/.test(token)) {
    throw new Error("Se requiere sesión CLIENT y API HTTPS (o local segura).");
  }
  const response = await fetch(API_BASE + path, {
    method, cache:"no-store", credentials:"omit",
    referrerPolicy:"no-referrer", signal:options?.signal,
    headers: {
      Authorization: "Bearer " + token, Accept:"application/json",
      ...(options?.body ? {"Content-Type":"application/json"} : {}),
      ...(options?.idempotencyKey ? {"Idempotency-Key":options.idempotencyKey} : {}),
    },
    ...(options?.body ? {body:JSON.stringify(options.body)} : {}),
  });
  if (!response.ok) throw new CheckoutHttpError(response.status,httpMessage(response.status));
  return (await response.json()) as unknown;
}
export async function fetchCheckoutQuote(
  token: string, cart: Cart, signal?: AbortSignal,
): Promise<CheckoutQuote> {
  const result = await request("GET","/api/checkout/quote",token,{signal});
  return parseCheckoutQuote(result,cart);
}
export async function createCheckoutOrder(
  token: string, address: ShippingAddress,
): Promise<CreatedOrder> {
  const result = await request("POST","/api/orders",token,{
    body:makeShippingAddressBody(address),
  });
  return parseCreatedOrder(result,address);
}
export async function startCheckoutPayment(
  token: string, order: CreatedOrder, key: string, appOrigin: string,
): Promise<SandboxSession> {
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]{7,79}$/.test(key)) {
    throw new Error("Clave de idempotencia incorrecta.");
  }
  const result = await request("POST",
    "/api/orders/" + order.id + "/payment-sessions",
    token,{idempotencyKey:key});
  return parseSandboxSession(result,order,appOrigin);
}
