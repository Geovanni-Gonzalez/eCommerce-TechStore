/**
 * BKL-017 — Contrato público de registro y autenticación BKL-015/016.
 * Las respuestas REST se consideran datos desconocidos hasta su validación.
 */
export type UserRole = "CLIENT" | "ADMIN";
export type AuthUser = {
  id: number; displayName: string; email: string; role: UserRole;
};
export type AuthSession = {
  accessToken: string; tokenType: "Bearer"; user: AuthUser;
};
export type Registration = {
  displayName: string; email: string; password: string;
};

const object = (value: unknown): Record<string, unknown> | null =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : null;
const nonEmpty = (value: unknown): value is string =>
  typeof value === "string" && value.trim().length > 0;
const validId = (value: unknown): value is number =>
  typeof value === "number" && Number.isSafeInteger(value) && value > 0;

export function parseAuthUser(value: unknown): AuthUser {
  const user = object(value);
  if (!user || !validId(user.id) || !nonEmpty(user.displayName)
      || !nonEmpty(user.email) || !["CLIENT", "ADMIN"].includes(String(user.role))) {
    throw new Error("La API devolvió una identidad no válida.");
  }
  return {
    id: user.id, displayName: user.displayName, email: user.email,
    role: user.role as UserRole,
  };
}
export function parseAuthSession(value: unknown): AuthSession {
  const session = object(value);
  if (!session || session.tokenType !== "Bearer"
      || !nonEmpty(session.accessToken) || !/^[A-Za-z0-9_-]{32,}$/.test(session.accessToken)) {
    throw new Error("La API devolvió una sesión no válida.");
  }
  return {
    accessToken: session.accessToken, tokenType: "Bearer",
    user: parseAuthUser(session.user),
  };
}
export function parseRegisteredClient(value: unknown): AuthUser {
  const user = parseAuthUser(value);
  if (user.role !== "CLIENT") throw new Error("El registro no devolvió una cuenta CLIENT.");
  return user;
}
export function roleDestination(role: UserRole): "/cuenta" | "/admin" {
  return role === "ADMIN" ? "/admin" : "/cuenta";
}

/** Validación amable en la UI; el servidor vuelve a validar todo. */
export function validateRegistration(input: Registration): string | null {
  const name = input.displayName.trim();
  const email = input.email.trim();
  if (name.length < 2 || name.length > 120 || /[\x00-\x1F\x7F]/.test(name)) {
    return "El nombre debe tener entre 2 y 120 caracteres válidos.";
  }
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return "Introduce un correo electrónico válido.";
  }
  if (input.password.length < 8 || input.password.length > 128
      || /[\x00-\x1F\x7F]/.test(input.password)
      || new TextEncoder().encode(input.password).length > 72) {
    return "La contraseña debe tener entre 8 y 128 caracteres y no superar 72 bytes UTF-8.";
  }
  return null;
}
