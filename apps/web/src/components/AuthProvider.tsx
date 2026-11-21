"use client";

import { createContext, useContext, useRef, useState } from "react";
import { parseAuthSession, type AuthSession, type AuthUser, type UserRole } from "@/lib/auth";
import { publicAuthPost, revokeAuthToken } from "@/lib/auth-client";

type AuthContextValue = {
  user: AuthUser | null;
  login(email: string, password: string): Promise<UserRole>;
  logout(): Promise<void>;
  getToken(): string | null;
};
const Context = createContext<AuthContextValue | null>(null);

/**
 * El Bearer opaco vive SOLO en un ref del provider (pestaña actual).
 * No se comparte mediante URL/cookies/storage ni se renderiza en el DOM.
 */
export default function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
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
    setUser(null);
    if (token) await revokeAuthToken(token);
  }
  const getToken = (): string | null => session.current?.accessToken ?? null;

  return (
    <Context.Provider value={{ user, login, logout, getToken }}>
      {children}
    </Context.Provider>
  );
}
export function useAuth(): AuthContextValue {
  const context = useContext(Context);
  if (!context) throw new Error("Falta AuthProvider en el árbol de la aplicación.");
  return context;
}
