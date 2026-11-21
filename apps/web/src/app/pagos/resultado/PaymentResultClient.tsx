"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useRef, useState } from "react";
import {
  classifyPaymentResult, isSafeApiOrigin, parseResultId,
  type VerifiedPaymentResult,
} from "@/lib/payment-result";

type Phase = "access" | "checking" | "verified" | "error";

const API_BASE = (
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

async function readJson(response: Response): Promise<unknown> {
  if (!response.ok) {
    throw new Error(
      response.status === 401 || response.status === 403
        ? "No se pudo autenticar o no tienes permiso sobre este pedido."
        : response.status === 404
          ? "El pedido o intento no existe, o no pertenece a esta cuenta."
          : "No fue posible consultar el servidor (HTTP " + response.status + ").",
    );
  }
  return (await response.json()) as unknown;
}

async function authenticatedGet(path: string, bearer: string): Promise<unknown> {
  if (!isSafeApiOrigin(API_BASE)) {
    throw new Error("API insegura: se requiere HTTPS (HTTP solo localhost).");
  }
  const response = await fetch(API_BASE + path, {
    method: "GET",
    headers: { Accept: "application/json", Authorization: "Bearer " + bearer },
    cache: "no-store",
    credentials: "omit",
    referrerPolicy: "no-referrer",
  });
  return readJson(response);
}

const labels = {
  approved: {
    heading: "Simulación aprobada",
    detail: "El backend confirmó este pedido en modo SANDBOX tras la conciliación verificada. No se ha realizado un cobro bancario real.",
  },
  rejected: {
    heading: "Simulación rechazada",
    detail: "El backend registró el intento como rechazado. No hay confirmación de pago. Puedes revisar el pedido antes de intentar otra operación.",
  },
  pending: {
    heading: "Resultado pendiente",
    detail: "No existe evidencia suficiente de confirmación. La autorización puede estar en proceso o necesitar conciliación; no se asume éxito ni rechazo.",
  },
} as const;

export default function PaymentResultClient() {
  const search = useSearchParams();
  const orderId = parseResultId(search.get("orderId"));
  const attemptId = parseResultId(search.get("attemptId"));

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [phase, setPhase] = useState<Phase>("access");
  const [verified, setVerified] = useState<VerifiedPaymentResult | null>(null);
  const [message, setMessage] = useState("");
  const [hasAccess, setHasAccess] = useState(false);
  const session = useRef<string | null>(null);

  const valid = orderId !== null && attemptId !== null;

  async function check(bearer: string) {
    if (orderId === null || attemptId === null) return;
    setVerified(null);
    setMessage("");
    setPhase("checking");
    try {
      // Ambas consultas requieren Bearer CLIENT propietario y deben coincidir.
      // Un query param o path /aprobado es solo un localizador, NUNCA evidencia.
      const [order, attempt] = await Promise.all([
        authenticatedGet("/api/orders/" + orderId, bearer),
        authenticatedGet(
          "/api/orders/" + orderId + "/payment-attempts/" + attemptId,
          bearer,
        ),
      ]);
      const actual = classifyPaymentResult(order, attempt, orderId, attemptId);
      setVerified(actual);
      setPhase("verified");
      // No navegar al cambiar el resultado: Next.js desmontaría este
      // componente y descartaría la sesión Bearer y el estado verificado.
      // El path y query string NUNCA son evidencia de pago.
    } catch (failure: unknown) {
      setVerified(null);
      setMessage(failure instanceof Error ? failure.message : "Consulta no disponible.");
      setPhase("error");
    }
  }

  async function login() {
    if (!valid || phase === "checking") return;
    setPhase("checking");
    setMessage("");
    setVerified(null);
    try {
      if (!isSafeApiOrigin(API_BASE)) {
        throw new Error("API insegura: se requiere HTTPS (HTTP solo localhost).");
      }
      const response = await fetch(API_BASE + "/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        cache: "no-store",
        credentials: "omit",
        referrerPolicy: "no-referrer",
        body: JSON.stringify({ email, password }),
      });
      const data = await readJson(response) as {
        accessToken?: unknown;
      };
      if (typeof data.accessToken !== "string" || !data.accessToken) {
        throw new Error("No fue posible validar la sesión.");
      }
      // El token solo vive en un ref de esta pestaña: nunca URL/cookie/storage.
      session.current = data.accessToken;
      setHasAccess(true);
      setPassword("");
      setEmail("");
      await check(data.accessToken);
    } catch (failure: unknown) {
      session.current = null;
      setHasAccess(false);
      setPassword("");
      setVerified(null);
      setMessage(failure instanceof Error ? failure.message : "Acceso no disponible.");
      setPhase("access");
    }
  }

  async function closeSession() {
    const bearer = session.current;
    session.current = null;
    setHasAccess(false);
    setVerified(null);
    setPassword("");
    setEmail("");
    setMessage("");
    setPhase("access");
    if (!bearer) return;
    try {
      await fetch(API_BASE + "/api/auth/logout", {
        method: "POST",
        headers: { Authorization: "Bearer " + bearer },
        credentials: "omit",
        cache: "no-store",
        referrerPolicy: "no-referrer",
      });
    } catch {
      // El token ya no reside en memoria, incluso si el servidor no responde.
    }
  }

  return (
    <section className="notice payment-result" aria-labelledby="titulo-resultado">
      <p className="eyebrow">TechStore · Consulta segura de resultado</p>
      <h1 id="titulo-resultado">Estado de tu simulación</h1>
      <p role="note">
        Esta demostración no mueve dinero ni confirma un pago bancario. La URL
        nunca determina el resultado: se consulta el backend con tu identidad.
      </p>

      {!valid ? (
        <p role="alert">
          Enlace incompleto. Para consultar un resultado necesitas el ID del
          pedido y el ID del intento generado por el servidor.
        </p>
      ) : (
        <>
          <p>Pedido #{orderId} · Intento #{attemptId}</p>

          {phase === "access" && (
            <form
              className="payment-result__form"
              onSubmit={(event) => {
                event.preventDefault();
                void login();
              }}
            >
              <h2>Verificar identidad</h2>
              <p>
                Ingresa con la cuenta propietaria del pedido. Tus credenciales
                y el token de acceso no se colocan en la URL ni se guardan
                en localStorage o sessionStorage.
              </p>
              <label htmlFor="result-email">Correo electrónico</label>
              <input id="result-email" name="email" type="email"
                autoComplete="username" required value={email}
                onChange={(event) => setEmail(event.target.value)} />
              <label htmlFor="result-password">Contraseña</label>
              <input id="result-password" name="password" type="password"
                autoComplete="current-password" required value={password}
                onChange={(event) => setPassword(event.target.value)} />
              <button type="submit" className="sandbox-submit">
                Consultar estado real del backend
              </button>
              {message && <p role="alert">{message}</p>}
            </form>
          )}

          {phase === "checking" && (
            <p role="status">Verificando el pedido y el intento con el servidor…</p>
          )}

          {phase === "error" && (
            <div role="alert" className="payment-result__status">
              <h2>No se pudo verificar</h2>
              <p>{message}</p>
              <p>No mostramos un resultado de pago sin consultar al servidor.</p>
              {hasAccess && (
                <button type="button" className="sandbox-submit"
                  onClick={() => {
                    if (session.current) void check(session.current);
                  }}>
                  Reintentar consulta
                </button>
              )}
            </div>
          )}

          {phase === "verified" && verified && (
            <div className={"payment-result__status payment-result__status--" +
              verified.kind} role="status" aria-live="polite">
              <h2>{labels[verified.kind].heading}</h2>
              <p>{labels[verified.kind].detail}</p>
              {verified.reason === "WAITING_RECONCILIATION" && (
                <p>El intento indica aprobación simulada, pero el pedido aún
                  no está confirmado. Se necesita conciliación del servidor.</p>
              )}
              <p>Información consultada al backend; no constituye recibo bancario.</p>
              <button type="button" className="sandbox-submit"
                onClick={() => {
                  if (session.current) void check(session.current);
                }}>
                Comprobar de nuevo
              </button>
            </div>
          )}

          {hasAccess && phase !== "checking" && (
            <p>
              <button type="button" className="payment-result__link-button"
                onClick={() => void closeSession()}>
                Cerrar consulta segura
              </button>
            </p>
          )}
        </>
      )}
      <p><Link href="/pedidos">Consultar mi historial de pedidos</Link></p>
      <p><Link href="/">Volver a TechStore</Link></p>
    </section>
  );
}
