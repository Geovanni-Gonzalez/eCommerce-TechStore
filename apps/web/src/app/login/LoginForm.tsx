"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useAuth } from "@/components/AuthProvider";
import { roleDestination } from "@/lib/auth";

export default function LoginForm() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const role = await login(email.trim().toLowerCase(), password);
      setPassword("");
      router.replace(roleDestination(role));
    } catch (cause: unknown) {
      setPassword("");
      setError(cause instanceof Error ? cause.message : "No se pudo iniciar sesión.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="auth-page" aria-labelledby="auth-title">
      <div className="auth-card">
        <p className="eyebrow">TechStore · Acceso</p>
        <h1 id="auth-title">Iniciar sesión</h1>
        <p>Ingresa con la cuenta registrada. Tu acceso depende del rol asignado por el servidor.</p>
        <form className="auth-form" onSubmit={(event) => void submit(event)}>
          <label htmlFor="auth-email">Correo electrónico</label>
          <input id="auth-email" type="email" name="email" autoComplete="username"
            required maxLength={254} value={email} disabled={busy}
            onChange={(event) => setEmail(event.target.value)} />
          <label htmlFor="auth-password">Contraseña</label>
          <input id="auth-password" type="password" name="password"
            autoComplete="current-password" required value={password} disabled={busy}
            onChange={(event) => setPassword(event.target.value)} />
          {error && <p className="auth-error" role="alert">{error}</p>}
          {busy && <p role="status">Verificando credenciales…</p>}
          <button className="auth-button" type="submit" disabled={busy}>
            {busy ? "Ingresando…" : "Ingresar a mi cuenta"}
          </button>
        </form>
        <p className="auth-footer">¿No tienes una cuenta? <Link href="/registro">Crear cuenta</Link></p>
        <p className="auth-help">Sesión únicamente en memoria de esta pestaña.
          No almacenamos la contraseña ni el token en el navegador.</p>
      </div>
    </section>
  );
}
