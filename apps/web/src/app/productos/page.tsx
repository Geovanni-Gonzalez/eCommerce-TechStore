import type { Metadata } from "next";
import { Suspense } from "react";
import CatalogClient from "./CatalogClient";
import "./catalog.css";

export const metadata: Metadata = {
  title: "Productos | TechStore",
  description: "Explora accesorios tecnológicos con precios y existencias consultados en la API pública.",
};

export default function ProductosPage() {
  return (
    <section className="catalog-page" aria-labelledby="catalog-title">
      <header className="catalog-intro">
        <p className="eyebrow">Catálogo público · TechStore</p>
        <h1 id="catalog-title">Explora nuestros productos</h1>
        <p className="lead">Precios en colones y disponibilidad consultados desde el catálogo.</p>
      </header>
      <Suspense fallback={<p role="status">Preparando catálogo…</p>}>
        <CatalogClient />
      </Suspense>
    </section>
  );
}
