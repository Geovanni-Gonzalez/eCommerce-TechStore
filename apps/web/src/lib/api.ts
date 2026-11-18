/**
 * Cliente HTTP reutilizable para la API Spring Boot.
 * NEXT_PUBLIC_API_URL debe apuntar al backend accesible desde el navegador.
 * No incluye contraseñas, sesiones ni tokens por defecto.
 */
const apiBaseUrl = (
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function apiGet<T>(
  path: `/${string}`,
  init?: Omit<RequestInit, "method">,
): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set("Accept", "application/json");

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    method: "GET",
    headers,
  });

  if (!response.ok) {
    throw new ApiError(response.status, `Error HTTP ${response.status}`);
  }

  return (await response.json()) as T;
}
