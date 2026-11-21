import type { Metadata } from "next";
import OrdersClient from "./OrdersClient";

export const metadata: Metadata = {
  title: "Mis pedidos | TechStore",
  description: "Historial, detalle y estados de tus pedidos, con acceso privado",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function OrdersPage() {
  return <OrdersClient />;
}
