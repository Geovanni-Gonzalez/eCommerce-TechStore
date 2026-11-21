import type { Metadata } from "next";
import CartClient from "./CartClient";
import "./cart.css";

export const metadata: Metadata = {
  title: "Mi carrito | TechStore",
  description: "Revisa y modifica artículos de tu carrito persistente.",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function CarritoPage() {
  return <CartClient />;
}
