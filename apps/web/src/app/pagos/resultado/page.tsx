import type { Metadata } from "next";
import { Suspense } from "react";
import PaymentResultClient from "./PaymentResultClient";

export const metadata: Metadata = {
  title: "Consultar resultado de pago | TechStore",
  description: "Resultado de simulación obtenido del backend, con autenticación",
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};

export default function PaymentResultPage() {
  return (
    <Suspense fallback={<p role="status">Preparando consulta segura…</p>}>
      <PaymentResultClient />
    </Suspense>
  );
}
