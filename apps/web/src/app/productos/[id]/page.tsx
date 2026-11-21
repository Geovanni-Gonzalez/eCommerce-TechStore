import type { Metadata } from "next";
import ProductDetailClient from "./ProductDetailClient";
import "../catalog.css";

export const metadata: Metadata = {
  title: "Detalle de producto | TechStore",
  description: "Características, precio y disponibilidad del producto consultados en la API.",
};

export default async function ProductDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <ProductDetailClient idParam={id} />;
}
