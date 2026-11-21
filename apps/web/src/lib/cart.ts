/**
 * BKL-029: contrato de la API BKL-028. El navegador nunca calcula precios
 * de compra ni decide disponibilidad; solo representa los valores del servidor.
 */
export type CartItem = {
  productId: number;
  sku: string;
  name: string;
  quantity: number;
  stock: number;
  available: boolean;
  unitPriceCrc: number;
  lineSubtotalCrc: number;
};
export type Cart = {
  items: CartItem[];
  totalItems: number;
  subtotalCrc: number;
  checkoutReady: boolean;
};
const record = (value: unknown): Record<string, unknown> | null =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : null;
const id = (value: unknown): value is number =>
  typeof value === "number" && Number.isSafeInteger(value) && value > 0;
const integer = (value: unknown): value is number =>
  typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
const money = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value) && value >= 0
    && Number.isSafeInteger(Math.round(value * 100));
const string = (value: unknown): value is string =>
  typeof value === "string" && value.trim().length > 0;

/** Cantidad del contrato de carrito: entero entre 1 y 99. */
export function parseCartQuantity(value: string): number | null {
  if (!/^[1-9][0-9]?$/.test(value)) return null;
  const quantity = Number(value);
  return quantity >= 1 && quantity <= 99 ? quantity : null;
}
export function validProductId(value: unknown): value is number {
  return id(value);
}
export function parseCart(value: unknown): Cart {
  const data = record(value);
  if (!data || !Array.isArray(data.items) || !integer(data.totalItems)
      || !money(data.subtotalCrc) || typeof data.checkoutReady !== "boolean") {
    throw new Error("La API devolvió un carrito con formato inesperado.");
  }
  const seen = new Set<number>();
  const items: CartItem[] = data.items.map((value: unknown) => {
    const row = record(value);
    if (!row || !id(row.productId) || seen.has(row.productId)
        || !string(row.sku) || !string(row.name)
        || !integer(row.quantity) || row.quantity < 1 || row.quantity > 99
        || !integer(row.stock) || typeof row.available !== "boolean"
        || !money(row.unitPriceCrc) || !money(row.lineSubtotalCrc)
        || (row.available && row.stock < row.quantity)
        || Math.round(row.unitPriceCrc * row.quantity * 100)
           !== Math.round(row.lineSubtotalCrc * 100)) {
      throw new Error("La API devolvió un artículo del carrito no válido.");
    }
    seen.add(row.productId);
    return {
      productId: row.productId, sku: row.sku, name: row.name,
      quantity: row.quantity, stock: row.stock, available: row.available,
      unitPriceCrc: row.unitPriceCrc, lineSubtotalCrc: row.lineSubtotalCrc,
    };
  });
  const count = items.reduce((sum, row) => sum + row.quantity, 0);
  const cents = items.reduce((sum, row) => sum + Math.round(row.lineSubtotalCrc * 100), 0);
  if (!Number.isSafeInteger(count) || !Number.isSafeInteger(cents)
      || count !== data.totalItems
      || cents !== Math.round(data.subtotalCrc * 100)
      || data.checkoutReady !== (items.length > 0 && items.every(row => row.available))) {
    throw new Error("Los totales del carrito no coinciden con sus artículos.");
  }
  return {
    items, totalItems: data.totalItems,
    subtotalCrc: data.subtotalCrc, checkoutReady: data.checkoutReady,
  };
}
