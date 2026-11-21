import { isSafeApiOrigin } from "@/lib/payment-result";
import { parseCart, validProductId, type Cart } from "@/lib/cart";

const API_BASE = (
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

export class CartHttpError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "CartHttpError";
  }
}
function requireApi(): string {
  if (!isSafeApiOrigin(API_BASE)) {
    throw new Error("Se requiere una API por HTTPS o HTTP local seguro.");
  }
  return API_BASE;
}
export function cartErrorMessage(status: number): string {
  switch (status) {
    case 400: return "La cantidad debe ser un número entero entre 1 y 99.";
    case 401: return "La sesión expiró. Inicia sesión de nuevo.";
    case 403: return "Esta cuenta no tiene permisos de carrito de cliente.";
    case 404: return "El producto o artículo ya no está disponible.";
    case 409: return "No hay existencias suficientes o cambió la cantidad disponible. Actualiza tu carrito.";
    default: return "No se pudo completar la operación en el servidor.";
  }
}
async function cartRequest(
  token: string,
  method: "GET" | "POST" | "PATCH" | "DELETE",
  path: string,
  body?: {productId?: number; quantity?: number},
  signal?: AbortSignal,
): Promise<unknown | null> {
  if (!token || !/^[A-Za-z0-9_-]{32,}$/.test(token)) {
    throw new Error("Debes iniciar sesión para usar el carrito.");
  }
  const response = await fetch(requireApi() + path, {
    method, signal, cache: "no-store", credentials: "omit",
    referrerPolicy: "no-referrer",
    headers: {
      Authorization: "Bearer " + token, Accept: "application/json",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new CartHttpError(response.status, cartErrorMessage(response.status));
  return response.status === 204 ? null : (await response.json()) as unknown;
}
export async function getCart(token: string, signal?: AbortSignal): Promise<Cart> {
  return parseCart(await cartRequest(token, "GET", "/api/cart", undefined, signal));
}
export async function addCartItem(token: string, productId: number, quantity = 1): Promise<Cart> {
  if (!validProductId(productId) || !Number.isInteger(quantity) || quantity < 1 || quantity > 99) {
    throw new Error("El identificador o la cantidad del producto no es válida.");
  }
  return parseCart(await cartRequest(token, "POST", "/api/cart/items", {productId, quantity}));
}
export async function updateCartItem(token: string, productId: number, quantity: number): Promise<Cart> {
  if (!validProductId(productId) || !Number.isInteger(quantity) || quantity < 1 || quantity > 99) {
    throw new Error("La cantidad debe ser un entero entre 1 y 99.");
  }
  return parseCart(await cartRequest(token, "PATCH",
    "/api/cart/items/" + productId, {quantity}));
}
export async function removeCartItem(token: string, productId: number): Promise<void> {
  if (!validProductId(productId)) throw new Error("Producto no válido.");
  await cartRequest(token, "DELETE", "/api/cart/items/" + productId);
}
export async function clearCart(token: string): Promise<void> {
  await cartRequest(token, "DELETE", "/api/cart");
}
