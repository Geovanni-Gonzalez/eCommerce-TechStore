"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { parseRegisteredClient, validateRegistration } from "@/lib/auth";
import { publicAuthPost } from "@/lib/auth-client";

export default function RegisterForm() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || created) return;
    setError("");
    const input = { displayName: name.trim(), email: email.trim().toLowerCase(), password };
    const invalid = validateRegistration(input);
    if (invalid) {
      setError(invalid);
      return;
    }
    setBusy(true);
    try {
      const raw = await publicAuthPost("/api/auth/register", input);
      const user = parseRegisteredClient(raw);
      if (user.email.toLowerCase() !== input.email) {
        throw new Error("El servidor devolvió una identidad inesperada.");
      }
      setPassword("");
      setCreated(true);
    } catch (cause: unknown) {
      setPassword("");
      setError(cause instanceof Error ? cause.message : "No se pudo registrar la cuenta.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="auth-page" aria-labelledby="register-title">
      <div className="auth-card">
        <p className="eyebrow">TechStore · Nuevos clientes</p>
        <h1 id="register-title">Crear cuenta</h1>
        {created ? (
          <div role="status" className="auth-success">
            <h2>Cuenta creada correctamente</h2>
            <p>El registro no inicia sesión automáticamente.
              Ingresa con tu correo y contraseña para continuar.</p>
            <Link className="button-link" href="/login">Ir a iniciar sesión</Link>
          </div>
        ) : (
          <>
            <p>Todos los registros públicos son de tipo cliente. No puedes asignarte permisos de administrador.</p>
            <form className="auth-form" onSubmit={(event) => void submit(event)}>
              <label htmlFor="register-name">Nombre completo</label>
              <input id="register-name" name="displayName" type="text" required
                autoComplete="name" minLength={2} maxLength={120} value={name}
                disabled={busy} onChange={(event) => setName(event.target.value)} />
              <label htmlFor="register-email">Correo electrónico</label>
              <input id="register-email" name="email" type="email" required
                autoComplete="email" maxLength={254} value={email}
                disabled={busy} onChange={(event) => setEmail(event.target.value)} />
              <label htmlFor="register-password">Contraseña</label>
              <input id="register-password" name="password" type="password"
                autoComplete="new-password" minLength={8} maxLength={128} required
                aria-describedby="register-password-help" value={password}
                disabled={busy} onChange={(event) => setPassword(event.target.value)} />
              <p id="register-password-help" className="auth-help">
                De 8 a 128 caracteres y máximo 72 bytes UTF-8.
              </p>
              {error && <p className="auth-error" role="alert">{error}</p>}
              {busy && <p role="status">Registrando cuenta en el servidor…</p>}
              <button className="auth-button" type="submit" disabled={busy}>
                {busy ? "Registrando…" : "Registrarme"}
              </button>
            </form>
            <p className="auth-footer">¿Ya tienes cuenta? <Link href="/login">Iniciar sesión</Link></p>
          </>
        )}
      </div>
    </section>
  );
}
