import type {OrderStatus,OrderSummary} from "./order-history";

/**
 * Filtros de PRESENTACIÓN: solo actúan sobre pedidos ya recibidos por el
 * backend autenticado. Nunca se consulta por ID arbitrario ni se cambia
 * la paginación segura del servidor.
 */
export type OrdersViewFilter = "ALL" | OrderStatus;

export function loadedOrderCounts(orders: readonly OrderSummary[]) {
  return {
    loaded: orders.length,
    pending: orders.filter(order => order.status === "PENDING").length,
    confirmed: orders.filter(order => order.status === "CONFIRMED").length,
    cancelled: orders.filter(order => order.status === "CANCELLED").length,
  };
}

export function filterLoadedOrders(
  orders: readonly OrderSummary[],
  filter: OrdersViewFilter,
  query: string,
): OrderSummary[] {
  const needle = query.trim().replace(/^#/, "").trim();
  if (needle && !/^\d+$/.test(needle)) return [];
  return orders.filter(order =>
    (filter === "ALL" || order.status === filter) &&
    (needle === "" || String(order.id).includes(needle))
  );
}
