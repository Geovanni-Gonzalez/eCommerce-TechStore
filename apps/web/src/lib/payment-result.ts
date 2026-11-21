/**
 * BKL-039: clasificar únicamente respuestas autenticadas del backend.
 * Nunca usar el segmento /aprobado, query strings, localStorage o returnUrl
 * como evidencia de éxito. El sandbox NO es un cobro bancario.
 */
export type PaymentResultKind = "approved" | "rejected" | "pending";
export type PaymentResultReason =
  | "SIMULATED_CONFIRMED"
  | "SIMULATED_DECLINED"
  | "WAITING_RECONCILIATION"
  | "INDETERMINATE";

export type VerifiedPaymentResult = {
  kind: PaymentResultKind;
  reason: PaymentResultReason;
};

type OrderSnapshot = {
  id: number;
  status: "PENDING" | "CONFIRMED" | "CANCELLED";
  paymentMode: "SIMULATED";
  totals: { totalCrc: number };
};

type AttemptSnapshot = {
  id: number;
  orderId: number;
  status: "STARTED" | "SIMULATED_APPROVED" | "SIMULATED_DECLINED" | "UNAVAILABLE" | "UNKNOWN";
  totalCrc: number;
};

const PENDING: VerifiedPaymentResult = {
  kind: "pending",
  reason: "INDETERMINATE",
};

function object(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function validId(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function cents(value: unknown): number | null {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0) return null;
  const converted = Math.round(value * 100);
  return Number.isSafeInteger(converted) ? converted : null;
}

function orderSnapshot(value: unknown): OrderSnapshot | null {
  const data = object(value);
  const totals = object(data?.totals);
  if (!data || !totals || !validId(data.id)
      || !["PENDING", "CONFIRMED", "CANCELLED"].includes(String(data.status))
      || data.paymentMode !== "SIMULATED"
      || cents(totals.totalCrc) === null) return null;
  return data as unknown as OrderSnapshot;
}

function attemptSnapshot(value: unknown): AttemptSnapshot | null {
  const data = object(value);
  if (!data || !validId(data.id) || !validId(data.orderId)
      || !["STARTED", "SIMULATED_APPROVED", "SIMULATED_DECLINED",
        "UNAVAILABLE", "UNKNOWN"].includes(String(data.status))
      || cents(data.totalCrc) === null) return null;
  return data as unknown as AttemptSnapshot;
}

/**
 * APPROVED solo si DOS recursos propiedad del usuario concuerdan:
 * el pedido fue CONFIRMED por conciliación BKL-040 y el intento
 * fue SIMULATED_APPROVED. Si falta evidencia, siempre PENDING.
 */
export function classifyPaymentResult(
  orderResponse: unknown, attemptResponse: unknown,
  expectedOrderId: number, expectedAttemptId: number,
): VerifiedPaymentResult {
  const order = orderSnapshot(orderResponse);
  const attempt = attemptSnapshot(attemptResponse);
  if (!validId(expectedOrderId) || !validId(expectedAttemptId)
      || !order || !attempt || order.id !== expectedOrderId
      || attempt.id !== expectedAttemptId
      || attempt.orderId !== order.id
      || cents(attempt.totalCrc) !== cents(order.totals.totalCrc)) return PENDING;

  if (order.status === "CONFIRMED" && attempt.status === "SIMULATED_APPROVED") {
    return { kind: "approved", reason: "SIMULATED_CONFIRMED" };
  }
  if (order.status !== "CONFIRMED" && attempt.status === "SIMULATED_DECLINED") {
    return { kind: "rejected", reason: "SIMULATED_DECLINED" };
  }
  if (order.status === "PENDING" && attempt.status === "SIMULATED_APPROVED") {
    return { kind: "pending", reason: "WAITING_RECONCILIATION" };
  }
  return PENDING;
}

/** Validar que IDs de la URL son solo localizadores; jamás prueban resultado. */
export function parseResultId(value: string | null): number | null {
  if (!value || !/^[1-9][0-9]{0,14}$/.test(value)) return null;
  const id = Number(value);
  return validId(id) ? id : null;
}

/** Nunca enviar email, contraseña o Bearer sobre HTTP remoto ni URLs con credenciales. */
export function isSafeApiOrigin(value: string): boolean {
  try {
    const url = new URL(value);
    const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
    return (url.protocol === "https:" || (url.protocol === "http:" && loopback))
      && !url.username && !url.password && !url.search && !url.hash
      && url.pathname === "/";
  } catch {
    return false;
  }
}
