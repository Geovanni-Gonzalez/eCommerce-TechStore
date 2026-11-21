import { isSafeApiOrigin } from "@/lib/payment-result";

/**
 * BKL-017 — Únicamente solicitudes HTTPS o HTTP loopback.
 * Las credenciales y los Bearer nunca se guardan en navegador ni se imprimen.
 */
const API_BASE = (
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

export class AuthHttpError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "AuthHttpError";
  }
}
function safeApiBase(): string {
  if (!isSafeApiOrigin(API_BASE)) {
    throw new Error("La API debe utilizar HTTPS o HTTP local seguro.");
  }
  return API_BASE;
}
async function jsonOrError(response: Response): Promise<unknown> {
  if (!response.ok) {
    let message = "No se pudo completar la solicitud.";
    if (response.status === 400) message = "Revisa los datos introducidos.";
    else if (response.status === 401) message = "Correo o contraseña incorrectos.";
    else if (response.status === 403) message = "No tienes permiso para acceder.";
    else if (response.status === 409) message = "Ya existe una cuenta con ese correo.";
    else if (response.status === 429) message = "Demasiados intentos. Inténtalo después.";
    else if (response.status >= 500) message = "El servidor no está disponible.";
    throw new AuthHttpError(response.status, message);
  }
  return (await response.json()) as unknown;
}
export async function publicAuthPost(
  path: "/api/auth/login" | "/api/auth/register",
  data: Record<string, string>,
): Promise<unknown> {
  const response = await fetch(safeApiBase() + path, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(data),
    credentials: "omit", cache: "no-store", referrerPolicy: "no-referrer",
  });
  return jsonOrError(response);
}
export async function adminAccessCheck(token: string): Promise<void> {
  const response = await fetch(safeApiBase() + "/api/admin/check", {
    method: "GET",
    headers: { Authorization: "Bearer " + token, Accept: "application/json" },
    credentials: "omit", cache: "no-store", referrerPolicy: "no-referrer",
  });
  if (!response.ok) {
    throw new AuthHttpError(response.status,
      response.status === 403 ? "No tienes permiso de administrador."
        : "No se pudo verificar el acceso administrativo.");
  }
}
export async function revokeAuthToken(token: string): Promise<void> {
  try {
    await fetch(safeApiBase() + "/api/auth/logout", {
      method: "POST", headers: { Authorization: "Bearer " + token },
      credentials: "omit", cache: "no-store", referrerPolicy: "no-referrer",
    });
  } catch {
    // El token se elimina localmente incluso ante problemas de red.
  }
}
