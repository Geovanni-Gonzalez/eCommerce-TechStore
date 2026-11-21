"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";

type Preview = {
  attemptId: number;
  orderId: number;
  totalCrc: number;
  currency: "CRC";
  expiresAt: string;
};
type Result = {
  id: number;
  orderId: number;
  totalCrc: number;
  status: "SIMULATED_APPROVED" | "SIMULATED_DECLINED" | "UNAVAILABLE" | "UNKNOWN";
  providerReference: string | null;
};
type Stage = "loading" | "ready" | "submitting" | "finished" | "error";

const API_BASE = (
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

async function request<T>(path: string, token: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ token }),
    cache: "no-store",
    credentials: "omit",
    referrerPolicy: "no-referrer",
  });
  if (!response.ok) {
    throw new Error(`La sesión no está disponible (HTTP ${response.status}).`);
  }
  return (await response.json()) as T;
}

export default function SandboxCheckoutClient() {
  const [token, setToken] = useState<string | null>(null);
  const [stage, setStage] = useState<Stage>("loading");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [result, setResult] = useState<Result | null>(null);
  const [error, setError] = useState("");

  const initializationStarted = useRef(false);

  useEffect(() => {
    // Strict Mode puede montar el efecto dos veces en desarrollo.
    if (initializationStarted.current) return;
    initializationStarted.current = true;
    const fragment = window.location.hash;
    // El fragmento nunca se transmite en la petición GET HTTP.
    window.history.replaceState(
      null, "", window.location.pathname + window.location.search,
    );
    const match = /^#session=([A-Za-z0-9_-]{43})$/.exec(fragment);
    // Se evita establecer estado de forma síncrona dentro de un efecto.
    void Promise.resolve().then(() => {
      if (!match) {
        setError("Enlace de sandbox ausente o incorrecto.");
        setStage("error");
        return;
      }
      const sessionToken = match[1];
      setToken(sessionToken);
      void request<Preview>(
        "/api/payments/sandbox/sessions/preview", sessionToken,
      ).then((data) => {
        setPreview(data);
        setStage("ready");
      }).catch((failure: unknown) => {
        setError(failure instanceof Error ? failure.message : "Sesión inválida.");
        setStage("error");
      });
    });
  }, []);

  async function confirmSimulation() {
    if (!token || stage !== "ready") return;
    setStage("submitting");
    try {
      const response = await request<Result>(
        "/api/payments/sandbox/sessions/complete", token,
      );
      setResult(response);
      setToken(null);
      setStage("finished");
    } catch (failure: unknown) {
      setToken(null);
      setError(failure instanceof Error ? failure.message : "Error de simulación.");
      setStage("error");
    }
  }

  return (
    <section className="notice" aria-labelledby="titulo-sandbox">
      <p className="eyebrow">Pasarela de demostración</p>
      <h1 id="titulo-sandbox">Pago simulado</h1>
      <p role="note">
        Esta página NO procesa dinero ni datos de tarjeta, y no confirma el
        pedido. El resultado es una simulación configurada por el servidor.
      </p>

      {stage === "loading" && <p role="status">Validando sesión…</p>}
      {stage === "error" && <p role="alert">{error}</p>}

      {(stage === "ready" || stage === "submitting") && preview && (
        <div>
          <p>Pedido #{preview.orderId} · Intento #{preview.attemptId}</p>
          <p>
            Total verificado por el servidor:{" "}
            <strong>
              {new Intl.NumberFormat("es-CR", {
                style: "currency", currency: "CRC",
              }).format(preview.totalCrc)}
            </strong>
          </p>
          <p>Sesión válida hasta: {new Date(preview.expiresAt).toLocaleString("es-CR")}</p>
          <button type="button" className="sandbox-submit"
            disabled={stage === "submitting"}
            onClick={() => void confirmSimulation()}>
            {stage === "submitting" ? "Simulando…" : "Ejecutar simulación"}
          </button>
        </div>
      )}
      {stage === "finished" && result && (
        <div role="status">
          <h2>Simulación completada</h2>
          <p>Estado del intento: <strong>{result.status}</strong>.</p>
          <p>
            Esta simulación no acredita cobro bancario. La confirmación del
            pedido depende de una conciliación verificada en el backend.
          </p>
          <p>
            <Link href={"/pagos/resultado?orderId=" + result.orderId +
              "&attemptId=" + result.id}>
              Consultar resultado verificado del pedido
            </Link>
          </p>
          {result.providerReference && <p>Referencia de sandbox: {result.providerReference}</p>}
        </div>
      )}
      <p><Link href="/">Volver a TechStore</Link></p>
    </section>
  );
}
