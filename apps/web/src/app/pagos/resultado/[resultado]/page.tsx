import { notFound } from "next/navigation";
import { Suspense } from "react";
import PaymentResultClient from "../PaymentResultClient";

export { metadata } from "../page";

export default async function ResultStatePage({
  params,
}: {
  params: Promise<{ resultado: string }>;
}) {
  const { resultado } = await params;
  // Una ruta directa /aprobado NO muestra éxito hasta verificar por API.
  if (!["aprobado", "rechazado", "pendiente"].includes(resultado)) {
    notFound();
  }
  return (
    <Suspense fallback={<p role="status">Preparando consulta segura…</p>}>
      <PaymentResultClient />
    </Suspense>
  );
}
