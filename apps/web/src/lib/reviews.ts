import { isSafeApiOrigin } from "@/lib/payment-result";

const BASE = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/\/$/, "");
export type ProductReview = { rating: number; comment: string; createdAt: string };
export type ReviewEligibility = {
  productId: number; eligible: boolean; hasActiveReview: boolean;
  reason: "ELIGIBLE" | "NOT_DELIVERED" | "ALREADY_REVIEWED";
};
export class ReviewHttpError extends Error {
  constructor(public readonly status: number) {
    super(status === 401 ? "Tu sesión expiró. Inicia sesión nuevamente."
      : status === 403 ? "Esta cuenta no puede publicar reseñas."
      : status === 404 ? "El producto ya no está disponible."
      : status === 409 ? "No cumples los requisitos o ya publicaste una reseña."
      : "No se pudo completar la operación. Inténtalo nuevamente.");
  }
}
function url(path: string): string {
  if (!isSafeApiOrigin(BASE)) throw new Error("Se requiere una API HTTPS o HTTP local seguro.");
  return BASE + path;
}
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Respuesta de reseñas inválida.");
  }
  return value as Record<string, unknown>;
}
export function parseReview(value: unknown): ProductReview {
  const o = object(value);
  if (!Number.isInteger(o.rating) || (o.rating as number) < 1 || (o.rating as number) > 5
      || typeof o.comment !== "string" || o.comment.length < 5 || o.comment.length > 1000
      || typeof o.createdAt !== "string" || !Number.isFinite(Date.parse(o.createdAt))) {
    throw new Error("La API devolvió una reseña inválida.");
  }
  return { rating: o.rating as number, comment: o.comment, createdAt: o.createdAt };
}
export function validateComment(comment: string): string | null {
  if (comment !== comment.trim() || comment.length < 5 || comment.length > 1000
      || /[\x00-\x1f\x7f-\x9f]/.test(comment)) {
    return "Escribe entre 5 y 1000 caracteres, sin espacios al inicio o al final.";
  }
  return null;
}
async function request(path: string, token?: string, body?: {rating: number; comment: string}, signal?: AbortSignal) {
  const response = await fetch(url(path), {
    method: body ? "POST" : "GET", cache: "no-store", credentials: "omit",
    referrerPolicy: "no-referrer", signal,
    headers: {
      Accept: "application/json",
      ...(token ? { Authorization: "Bearer " + token } : {}),
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new ReviewHttpError(response.status);
  return (await response.json()) as unknown;
}
export async function listReviews(productId: number, signal?: AbortSignal): Promise<ProductReview[]> {
  const value = await request("/api/reviews/products/" + productId, undefined, undefined, signal);
  if (!Array.isArray(value) || value.length > 50) throw new Error("Listado de reseñas inválido.");
  return value.map(parseReview);
}
export async function getReviewEligibility(token: string, productId: number, signal?: AbortSignal): Promise<ReviewEligibility> {
  const o = object(await request("/api/reviews/products/" + productId + "/eligibility", token, undefined, signal));
  if (o.productId !== productId || typeof o.eligible !== "boolean"
      || typeof o.hasActiveReview !== "boolean"
      || !["ELIGIBLE", "NOT_DELIVERED", "ALREADY_REVIEWED"].includes(String(o.reason))) {
    throw new Error("Elegibilidad inválida.");
  }
  return {
    productId, eligible: o.eligible as boolean,
    hasActiveReview: o.hasActiveReview as boolean,
    reason: o.reason as ReviewEligibility["reason"],
  };
}
export async function postReview(token: string, productId: number, rating: number, comment: string): Promise<ProductReview> {
  if (!Number.isInteger(rating) || rating < 1 || rating > 5 || validateComment(comment)) {
    throw new Error("Revisa la calificación y el comentario.");
  }
  return parseReview(await request("/api/reviews/products/" + productId, token, { rating, comment }));
}
