"use client";

import { createContext, useCallback, useContext, useRef, useState } from "react";
import { parseAuthSession, type AuthSession, type AuthUser, type UserRole } from "@/lib/auth";
import { publicAuthPost, revokeAuthToken } from "@/lib/auth-client";
import type { ShippingAddress } from "@/lib/shipping-address";

type AuthContextValue = {
  user: AuthUser | null;
  login(email: string, password: string): Promise<UserRole>;
  logout(): Promise<void>;
  getToken(): string | null;
  deliveryDraft: ShippingAddress | null;
  saveDeliveryDraft(value: ShippingAddress): void;
  clearDeliveryDraft(): void;
};
const Context = createContext<AuthContextValue | null>(null);

/**
 * El Bearer opaco vive SOLO en un ref del provider (pestaña actual).
 * No se comparte mediante URL/cookies/storage ni se renderiza en el DOM.
 */
export default function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [deliveryDraft, setDeliveryDraft] = useState<ShippingAddress | null>(null);
  const session = useRef<AuthSession | null>(null);
  const generation = useRef(0);

  async function login(email: string, password: string): Promise<UserRole> {
    const request = ++generation.current;
    const response = await publicAuthPost("/api/auth/login", { email, password });
    const result = parseAuthSession(response);
    if (generation.current !== request) {
      void revokeAuthToken(result.accessToken);
      throw new Error("Este intento de ingreso fue cancelado.");
    }
    const previous = session.current;
    session.current = result;
    setDeliveryDraft(null); // No transferir dirección entre sesiones o cuentas.
    setUser(result.user);
    if (previous && previous.accessToken !== result.accessToken) {
      void revokeAuthToken(previous.accessToken);
    }
    return result.user.role;
  }
  async function logout() {
    ++generation.current;
    const token = session.current?.accessToken ?? null;
    session.current = null;
    setDeliveryDraft(null); // Borrar PII de dirección inmediatamente al salir.
    setUser(null);
    if (token) await revokeAuthToken(token);
  }
  const getToken = useCallback((): string | null => session.current?.accessToken ?? null, []);
  function saveDeliveryDraft(value: ShippingAddress) {
    if (session.current?.user.role !== "CLIENT") {
      throw new Error("La dirección requiere una sesión CLIENT.");
    }
    setDeliveryDraft(value);
  }
  const clearDeliveryDraft = () => setDeliveryDraft(null);

  return (
    <Context.Provider value={{ user, login, logout, getToken,
      deliveryDraft, saveDeliveryDraft, clearDeliveryDraft }}>
      {children}
    </Context.Provider>
  );
}
export function useAuth(): AuthContextValue {
  const context = useContext(Context);
  if (!context) throw new Error("Falta AuthProvider en el árbol de la aplicación.");
  return context;
}
