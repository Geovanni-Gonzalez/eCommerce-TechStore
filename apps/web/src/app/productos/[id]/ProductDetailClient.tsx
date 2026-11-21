"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError, apiGet } from "@/lib/api";
import {
  type CatalogProduct, formatCatalogCrc, parseProduct, parseProductId,
} from "@/lib/catalog";
import ProductPhoto from "../ProductPhoto";

type Result = {
  forId: number;
  product?: CatalogProduct;
  notFound?: boolean;
  error?: boolean;
};

export default function ProductDetailClient({ idParam }: { idParam: string }) {
  const id = parseProductId(idParam);
  const [result, setResult] = useState<Result | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (id === null) return;
    const controller = new AbortController();
    apiGet<unknown>(`/api/products/${id}`, {
      cache: "no-store", signal: controller.signal,
    })
      .then((payload) => {
        const product = parseProduct(payload, id);
        if (!controller.signal.aborted) setResult({ forId: id, product });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({
            forId: id,
            notFound: error instanceof ApiError && error.status === 404,
            error: true,
          });
        }
      });
    return () => controller.abort();
  }, [id, retry]);

  const current = result?.forId === id ? result : null;
  return (
    <section className="catalog-page" aria-labelledby="product-title">
      <nav className="catalog-breadcrumb" aria-label="Ruta de navegación">
        <Link href="/productos">Productos</Link><span aria-hidden="true">/</span>
        <span>Detalle</span>
      </nav>
      {id === null || current?.notFound ? (
        <div className="catalog-feedback" role="status">
          <h1 id="product-title">Producto no disponible</h1>
          <p>Este producto no existe o ya no forma parte del catálogo activo.</p>
          <Link href="/productos">Volver al catálogo</Link>
        </div>
      ) : !current ? (
        <div role="status" className="catalog-feedback">
          <h1 id="product-title">Cargando producto…</h1>
          <p>Consultando precio y existencias en la API.</p>
        </div>
      ) : current.error || !current.product ? (
        <div className="catalog-feedback" role="alert">
          <h1 id="product-title">No se pudo consultar el producto</h1>
          <p>Comprueba la conexión con la API e inténtalo nuevamente.</p>
          <button type="button" className="catalog-button" onClick={() => {
            setResult(null);
            setRetry((n) => n + 1);
          }}>Reintentar</button>
        </div>
      ) : (
        <article className="catalog-product">
          <ProductPhoto key={current.product.imageUrl ?? "empty"}
            name={current.product.name} imageUrl={current.product.imageUrl} priority />
          <div className="catalog-product__content">
            <p className="eyebrow">{current.product.category?.name ?? "Accesorio tecnológico"}</p>
            <h1 id="product-title">{current.product.name}</h1>
            <p className="catalog-product__sku">Referencia: {current.product.sku}</p>
            <p className="catalog-product__price">{formatCatalogCrc(current.product.priceCrc)}</p>
            <p className={current.product.stock > 0
              ? "catalog-stock catalog-stock--available" : "catalog-stock catalog-stock--none"}>
              {current.product.stock > 0
                ? `Disponible · ${current.product.stock} unidad${current.product.stock === 1 ? "" : "es"}`
                : "Sin existencias"}
            </p>
            <h2>Descripción</h2>
            <p>{current.product.description?.trim() || "Este producto no tiene descripción publicada."}</p>
            <p className="catalog-product__note">La compra desde la interfaz se habilitará
              con el carrito y checkout visual (BKL-029 y BKL-034).</p>
            <Link className="catalog-detail-link" href="/productos">← Volver al catálogo</Link>
          </div>
        </article>
      )}
    </section>
  );
}
