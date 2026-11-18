/**
 * Cliente HTTP reutilizable para la API Spring Boot.
 *
 * En los componentes de servidor, la variable API_URL puede apuntar a una
 * dirección interna. Para llamadas desde el navegador, usa NEXT_PUBLIC_API_URL.
 * No incluye credenciales ni tokens por defecto.
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
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    method: "GET",
    headers: {
      Accept: "application/json",
      ...init?.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, `Error HTTP ${response.status}`);
  }

  return (await response.json()) as T;
}
