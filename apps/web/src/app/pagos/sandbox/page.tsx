import type { Metadata } from "next";
import SandboxCheckoutClient from "./SandboxCheckoutClient";

export const metadata: Metadata = {
  title: "Pago simulado | TechStore",
  description: "Demostración de pago sandbox, sin cargos reales",
  referrer: "no-referrer",
};

export default function SandboxCheckoutPage() {
  return <SandboxCheckoutClient />;
}
