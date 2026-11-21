import type { Metadata } from "next";
import DeliveryClient from "./DeliveryClient";
import "./delivery.css";

export const metadata: Metadata = {
  title: "Dirección de entrega | TechStore",
  description: "Introduce y revisa la dirección para entregar tu pedido en Costa Rica.",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function DeliveryPage() {
  return <DeliveryClient />;
}
