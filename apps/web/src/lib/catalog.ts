/**
 * BKL-019 — contrato público de catálogo (API Spring Boot).
 * Validamos respuestas desconocidas antes de representar existencias o precios.
 */
export type ProductCategory = { id: number; name: string };
export type CatalogProduct = {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  priceCrc: number;
  stock: number;
  active: true;
  category: ProductCategory | null;
  brand: string | null;
  imageUrl: string | null;
};
export type CatalogPage = {
  items: CatalogProduct[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export const CATALOG_SIZE = 12;
const asRecord = (v: unknown): Record<string, unknown> | null =>
  v !== null && typeof v === "object" && !Array.isArray(v)
    ? v as Record<string, unknown> : null;
const positiveId = (v: unknown): v is number =>
  typeof v === "number" && Number.isSafeInteger(v) && v > 0;
const nonEmpty = (v: unknown): v is string =>
  typeof v === "string" && v.trim().length > 0;
const nonNegativeInt = (v: unknown): v is number =>
  typeof v === "number" && Number.isSafeInteger(v) && v >= 0;
const crcAmount = (v: unknown): v is number =>
  typeof v === "number" && Number.isFinite(v) && v >= 0 &&
  Number.isSafeInteger(Math.round(v * 100));

/** Rechazar URL peligrosas y recursos HTTP remotos inseguros. */
export function safeImageUrl(value: unknown): string | null {
  if (typeof value !== "string" || !value.trim()) return null;
  try {
    const url = new URL(value);
    if (url.username || url.password) return null;
    if (url.protocol === "https:") return url.href;
    if (url.protocol === "http:" && ["localhost", "127.0.0.1"].includes(url.hostname)) {
      return url.href;
    }
    return null;
  } catch {
    return null;
  }
}

export function parseProduct(value: unknown, expectedId?: number): CatalogProduct {
  const p = asRecord(value);
  if (!p || !positiveId(p.id) || (expectedId !== undefined && p.id !== expectedId)
      || !nonEmpty(p.sku) || !nonEmpty(p.name) || !crcAmount(p.priceCrc)
      || !nonNegativeInt(p.stock) || p.active !== true
      || !(p.description == null || typeof p.description === "string")) {
    throw new Error("La API devolvió un producto con formato inesperado.");
  }
  const rawCategory = p.category == null ? null : asRecord(p.category);
  if (p.category != null && (!rawCategory || !positiveId(rawCategory.id)
      || !nonEmpty(rawCategory.name))) {
    throw new Error("La API devolvió una categoría no válida.");
  }
  return {
    id: p.id, sku: p.sku, name: p.name,
    description: p.description == null ? null : p.description as string,
    priceCrc: p.priceCrc, stock: p.stock, active: true,
    category: rawCategory
      ? { id: rawCategory.id as number, name: rawCategory.name as string } : null,
    brand: p.brand == null ? null : (typeof p.brand === "string"
      && p.brand.trim().length > 0 ? p.brand.trim() : null),
    imageUrl: safeImageUrl(p.imageUrl),
  };
}

export function parseCatalogPage(value: unknown, expectedPage: number): CatalogPage {
  const p = asRecord(value);
  if (!p || !Array.isArray(p.items) || !nonNegativeInt(p.page)
      || p.page !== expectedPage || p.size !== CATALOG_SIZE
      || !nonNegativeInt(p.totalElements) || !nonNegativeInt(p.totalPages)
      || p.items.length > CATALOG_SIZE
      || (p.totalElements === 0 && (p.totalPages !== 0 || p.items.length !== 0))
      || (p.totalElements > 0 && p.totalPages === 0)) {
    throw new Error("La API devolvió una página de catálogo no válida.");
  }
  return {
    items: p.items.map((item: unknown) => parseProduct(item)),
    page: p.page, size: p.size,
    totalElements: p.totalElements, totalPages: p.totalPages,
  };
}

export function parseCatalogPageQuery(value: string | null): number | null {
  if (value === null) return 0;
  if (!/^(0|[1-9][0-9]*)$/.test(value)) return null;
  const page = Number(value);
  return Number.isSafeInteger(page) && page <= 2147483647 ? page : null;
}

export function parseProductId(value: string): number | null {
  if (!/^[1-9][0-9]*$/.test(value)) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) ? id : null;
}

export function formatCatalogCrc(amount: number): string {
  return new Intl.NumberFormat("es-CR", {
    style: "currency", currency: "CRC", minimumFractionDigits: 2,
  }).format(amount);
}
