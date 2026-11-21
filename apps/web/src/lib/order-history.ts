/**
 * BKL-043: Validación de respuestas de la API BKL-042, antes de renderizar.
 * Nada del navegador ni de una URL define propietario, cobro o estado.
 */
export type OrderStatus = "PENDING" | "CONFIRMED" | "CANCELLED";
export type PaymentMode = "SIMULATED";
export type MoneyTotals = {
  subtotalCrc: number; shippingCrc: number; taxCrc: number; totalCrc: number;
};
export type OrderSummary = {
  id: number; status: OrderStatus; paymentMode: PaymentMode;
  totals: MoneyTotals; createdAt: string;
};
export type OrderPage = {
  items: OrderSummary[]; hasMore: boolean; nextBeforeId: number | null;
};
export type OrderItem = {
  productId: number; name: string; quantity: number;
  unitPriceCrc: number; lineTotalCrc: number;
};
export type ShippingAddress = {
  province: string; canton: string; district: string; details: string;
  recipientName: string; phone: string;
};
export type OrderDetail = OrderSummary & {
  items: OrderItem[]; shippingAddress: ShippingAddress;
};
export type OrderHistoryEntry = {
  fromStatus: OrderStatus | null; toStatus: OrderStatus;
  actorType: "CLIENT" | "ADMIN" | "SYSTEM";
  reason: "CREATED" | "BASELINE" | "CLIENT_CANCEL" | "ADMIN_CANCEL"
    | "EXPIRED" | "SANDBOX_VERIFIED";
  changedAt: string;
};

const ORDER_STATES = ["PENDING", "CONFIRMED", "CANCELLED"] as const;
const CHANGE_REASONS = [
  "CREATED", "BASELINE", "CLIENT_CANCEL", "ADMIN_CANCEL",
  "EXPIRED", "SANDBOX_VERIFIED",
] as const;

const record = (value: unknown): Record<string, unknown> | null =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : null;
export const isOrderId = (id: unknown): id is number =>
  typeof id === "number" && Number.isSafeInteger(id) && id > 0;
const money = (v: unknown): v is number =>
  typeof v === "number" && Number.isFinite(v) && v >= 0 &&
  Number.isSafeInteger(Math.round(v * 100));
const nonEmpty = (v: unknown): v is string =>
  typeof v === "string" && v.trim().length > 0;
const date = (v: unknown): v is string =>
  nonEmpty(v) && Number.isFinite(Date.parse(v));

function totals(value: unknown): MoneyTotals {
  const t = record(value);
  if (!t || !money(t.subtotalCrc) || !money(t.shippingCrc)
      || !money(t.taxCrc) || !money(t.totalCrc)) {
    throw new Error("Los importes del pedido no tienen un formato confiable.");
  }
  return {
    subtotalCrc: t.subtotalCrc, shippingCrc: t.shippingCrc,
    taxCrc: t.taxCrc, totalCrc: t.totalCrc,
  };
}

export function parseOrderSummary(value: unknown): OrderSummary {
  const o = record(value);
  if (!o || !isOrderId(o.id) || !ORDER_STATES.includes(o.status as OrderStatus)
      || o.paymentMode !== "SIMULATED" || !date(o.createdAt)) {
    throw new Error("La API devolvió un pedido con formato inesperado.");
  }
  return {
    id: o.id, status: o.status as OrderStatus, paymentMode: "SIMULATED",
    totals: totals(o.totals), createdAt: o.createdAt,
  };
}

export function parseOrderPage(value: unknown): OrderPage {
  const data = record(value);
  if (!data || !Array.isArray(data.items) || typeof data.hasMore !== "boolean"
      || !(data.nextBeforeId === null || isOrderId(data.nextBeforeId))) {
    throw new Error("La paginación de pedidos no es válida.");
  }
  const items = data.items.map(parseOrderSummary);
  if (items.some((o, i) => i > 0 && o.id >= items[i - 1].id)
      || (data.hasMore && (items.length === 0
        || data.nextBeforeId !== items.at(-1)?.id))
      || (!data.hasMore && data.nextBeforeId !== null)) {
    throw new Error("La API devolvió un cursor de pedidos inconsistente.");
  }
  return {
    items, hasMore: data.hasMore,
    nextBeforeId: data.nextBeforeId as number | null,
  };
}

export function parseOrderDetail(value: unknown, expectedId: number): OrderDetail {
  const order = parseOrderSummary(value);
  const data = record(value)!;
  const addr = record(data.shippingAddress);
  if (!isOrderId(expectedId) || order.id !== expectedId
      || !Array.isArray(data.items) || data.items.length === 0 || !addr
      || ![addr.province, addr.canton, addr.district, addr.details,
        addr.recipientName, addr.phone].every(nonEmpty)) {
    throw new Error("El detalle recibido no corresponde al pedido solicitado.");
  }
  const items = data.items.map((value: unknown): OrderItem => {
    const item = record(value);
    if (!item || !isOrderId(item.productId) || !nonEmpty(item.name)
        || !Number.isSafeInteger(item.quantity) || (item.quantity as number) < 1
        || !money(item.unitPriceCrc) || !money(item.lineTotalCrc)) {
      throw new Error("El pedido incluye líneas con formato inesperado.");
    }
    return {
      productId: item.productId, name: item.name,
      quantity: item.quantity as number,
      unitPriceCrc: item.unitPriceCrc, lineTotalCrc: item.lineTotalCrc,
    };
  });
  return {
    ...order, items,
    shippingAddress: {
      province: addr.province as string, canton: addr.canton as string,
      district: addr.district as string, details: addr.details as string,
      recipientName: addr.recipientName as string, phone: addr.phone as string,
    },
  };
}

export function parseOrderHistory(value: unknown): OrderHistoryEntry[] {
  if (!Array.isArray(value)) {
    throw new Error("No se pudo interpretar el historial de estados.");
  }
  return value.map((entry: unknown): OrderHistoryEntry => {
    const e = record(entry);
    if (!e || !(e.fromStatus === null || ORDER_STATES.includes(e.fromStatus as OrderStatus))
        || !ORDER_STATES.includes(e.toStatus as OrderStatus)
        || !["CLIENT", "ADMIN", "SYSTEM"].includes(String(e.actorType))
        || !CHANGE_REASONS.includes(e.reason as OrderHistoryEntry["reason"])
        || !date(e.changedAt)) {
      throw new Error("El historial contiene un cambio de estado inválido.");
    }
    return {
      fromStatus: e.fromStatus as OrderStatus | null,
      toStatus: e.toStatus as OrderStatus,
      actorType: e.actorType as OrderHistoryEntry["actorType"],
      reason: e.reason as OrderHistoryEntry["reason"],
      changedAt: e.changedAt as string,
    };
  });
}

export const STATUS_COPY: Record<OrderStatus, { title: string; description: string }> = {
  PENDING: {
    title: "Pendiente",
    description: "El pedido todavía no se ha confirmado. No se presume un pago realizado.",
  },
  CONFIRMED: {
    title: "Confirmado (sandbox)",
    description: "El pedido fue confirmado en la simulación; no demuestra un cobro bancario real.",
  },
  CANCELLED: {
    title: "Cancelado",
    description: "El pedido fue cancelado. No se debe interpretar como una devolución bancaria.",
  },
};
export const REASON_COPY: Record<OrderHistoryEntry["reason"], string> = {
  CREATED: "Pedido creado",
  BASELINE: "Estado registrado inicialmente",
  CLIENT_CANCEL: "Cancelado por el cliente",
  ADMIN_CANCEL: "Cancelado por administración",
  EXPIRED: "Reserva vencida",
  SANDBOX_VERIFIED: "Confirmación de simulación verificada",
};
export const ACTOR_COPY: Record<OrderHistoryEntry["actorType"], string> = {
  CLIENT: "Cliente", ADMIN: "Administración", SYSTEM: "Sistema",
};
export const formatCrc = (amount: number): string =>
  new Intl.NumberFormat("es-CR", {
    style: "currency", currency: "CRC", minimumFractionDigits: 2,
  }).format(amount);
export const formatOrderDate = (value: string): string =>
  new Intl.DateTimeFormat("es-CR", {
    dateStyle: "medium", timeStyle: "short", timeZone: "America/Costa_Rica",
  }).format(new Date(value));
