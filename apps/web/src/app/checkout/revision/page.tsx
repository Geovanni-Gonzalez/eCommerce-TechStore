import type { Metadata } from "next";
import ReviewClient from "./ReviewClient";
import "./review.css";

export const metadata: Metadata = {
  title: "Revisar pedido | TechStore",
  description: "Revisa dirección, artículos, envío e impuestos antes de confirmar un pedido.",
  robots: {index:false,follow:false},
  referrer: "no-referrer",
};

export default function CheckoutReviewPage() {
  return <ReviewClient />;
}
