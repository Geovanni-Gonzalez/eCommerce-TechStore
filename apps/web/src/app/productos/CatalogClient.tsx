"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiGet } from "@/lib/api";
import {
  CATALOG_SIZE, type CatalogPage, formatCatalogCrc,
  parseCatalogPage, parseCatalogPageQuery,
} from "@/lib/catalog";
import ProductPhoto from "./ProductPhoto";

type Result = {
  forPage: number;
  data?: CatalogPage;
  error?: boolean;
};

export default function CatalogClient() {
  const searchParams = useSearchParams();
  const currentPage = parseCatalogPageQuery(searchParams.get("page"));
  const [result, setResult] = useState<Result | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (currentPage === null) return;
    const controller = new AbortController();
    apiGet<unknown>(`/api/products?page=${currentPage}&size=${CATALOG_SIZE}`, {
      signal: controller.signal,
      cache: "no-store",
    })
      .then((payload) => {
        const data = parseCatalogPage(payload, currentPage);
        if (!controller.signal.aborted) setResult({ forPage: currentPage, data });
      })
      .catch(() => {
        if (!controller.signal.aborted) setResult({ forPage: currentPage, error: true });
      });
    return () => controller.abort();
  }, [currentPage, retry]);

  if (currentPage === null) {
    return (
      <div className="catalog-feedback" role="alert">
        <h2>Página no válida</h2>
        <p>El número de página solicitado no es válido.</p>
        <Link href="/productos">Volver al catálogo</Link>
      </div>
    );
  }

  const current = result?.forPage === currentPage ? result : null;
  if (!current) return <p className="catalog-feedback" role="status">Cargando productos de TechStore…</p>;
  if (current.error || !current.data) {
    return (
      <div className="catalog-feedback" role="alert">
        <h2>No se pudo cargar el catálogo</h2>
        <p>Comprueba que la API esté disponible e inténtalo de nuevo.</p>
        <button type="button" className="catalog-button" onClick={() => {
          setResult(null);
          setRetry((n) => n + 1);
        }}>Reintentar</button>
      </div>
    );
  }

  const { items, totalElements, totalPages } = current.data;
  return (
    <>
      <div className="catalog-toolbar">
        <p role="status">
          {totalElements === 0 ? "No hay productos publicados." :
            `${totalElements} producto${totalElements === 1 ? "" : "s"} en el catálogo`}
        </p>
        {totalPages > 0 && <span>Página {currentPage + 1} de {totalPages}</span>}
      </div>
      {items.length === 0 ? (
        <div className="catalog-feedback">
          <h2>{totalElements === 0 ? "Catálogo vacío" : "Sin resultados en esta página"}</h2>
          <p>{totalElements === 0
            ? "Todavía no hay productos activos. Puedes volver más adelante."
            : "Prueba volver al inicio del catálogo."}</p>
          {currentPage > 0 && <Link href="/productos">Primera página</Link>}
        </div>
      ) : (
        <ul className="catalog-grid">
          {items.map((product) => (
            <li key={product.id}>
              <article className="catalog-card">
                <ProductPhoto key={product.imageUrl ?? "empty"} name={product.name}
                  imageUrl={product.imageUrl} />
                <div className="catalog-card__body">
                  <p className="catalog-card__category">{product.category?.name ?? "Accesorio tecnológico"}</p>
                  <h2><Link href={`/productos/${product.id}`}>{product.name}</Link></h2>
                  <p className="catalog-price">{formatCatalogCrc(product.priceCrc)}</p>
                  <p className={product.stock > 0
                    ? "catalog-stock catalog-stock--available" : "catalog-stock catalog-stock--none"}>
                    {product.stock > 0
                      ? `Disponible · ${product.stock} unidad${product.stock === 1 ? "" : "es"}`
                      : "Agotado"}
                  </p>
                  <Link className="catalog-detail-link" href={`/productos/${product.id}`}>
                    Ver detalle <span aria-hidden="true">→</span>
                  </Link>
                </div>
              </article>
            </li>
          ))}
        </ul>
      )}

      {totalPages > 1 && (
        <nav className="catalog-pagination" aria-label="Paginación del catálogo">
          {currentPage > 0
            ? <Link href={currentPage === 1 ? "/productos" :
                `/productos?page=${currentPage - 1}`}>← Anterior</Link>
            : <span aria-disabled="true">← Anterior</span>}
          <span aria-current="page">Página {currentPage + 1}</span>
          {currentPage + 1 < totalPages
            ? <Link href={`/productos?page=${currentPage + 1}`}>Siguiente →</Link>
            : <span aria-disabled="true">Siguiente →</span>}
        </nav>
      )}
    </>
  );
}
