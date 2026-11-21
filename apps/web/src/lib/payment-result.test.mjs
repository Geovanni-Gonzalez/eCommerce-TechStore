import test from "node:test";
import assert from "node:assert/strict";
import { classifyPaymentResult, parseResultId } from "./payment-result.ts";

const order = (status = "PENDING") => ({
  id: 27, status, paymentMode: "SIMULATED",
  totals: { totalCrc: 1525.25 },
});
const attempt = (status = "STARTED") => ({
  id: 98, orderId: 27, status, totalCrc: 1525.25,
});
const classify = (o, a) => classifyPaymentResult(o, a, 27, 98);

test("BKL-039: aprobado únicamente con CONFIRMED + SIMULATED_APPROVED concordantes", () => {
  assert.deepEqual(classify(order("CONFIRMED"), attempt("SIMULATED_APPROVED")),
    { kind: "approved", reason: "SIMULATED_CONFIRMED" });
  for (const status of ["STARTED", "UNKNOWN", "UNAVAILABLE",
    "SIMULATED_DECLINED"]) {
    assert.equal(classify(order("CONFIRMED"), attempt(status)).kind, "pending");
  }
});

test("BKL-039: sandbox aprobado sin webhook confirmado permanece pendiente", () => {
  assert.deepEqual(classify(order(), attempt("SIMULATED_APPROVED")),
    { kind: "pending", reason: "WAITING_RECONCILIATION" });
  assert.equal(classify(order("CANCELLED"), attempt("SIMULATED_APPROVED")).kind,
    "pending");
});

test("BKL-039: rechazo solo si intento verificado es SIMULATED_DECLINED", () => {
  assert.deepEqual(classify(order(), attempt("SIMULATED_DECLINED")),
    { kind: "rejected", reason: "SIMULATED_DECLINED" });
  assert.equal(classify(order("CANCELLED"), attempt("SIMULATED_DECLINED")).kind,
    "rejected");
  for (const status of ["STARTED", "UNKNOWN", "UNAVAILABLE"]) {
    assert.equal(classify(order(), attempt(status)).kind, "pending");
  }
});

test("BKL-039: IDs, propietario, importe o estado inconsistentes nunca aprueban", () => {
  const yes = order("CONFIRMED");
  const paid = attempt("SIMULATED_APPROVED");
  assert.equal(classify({ ...yes, id: 29 }, paid).kind, "pending");
  assert.equal(classify(yes, { ...paid, orderId: 29 }).kind, "pending");
  assert.equal(classify(yes, { ...paid, id: 97 }).kind, "pending");
  assert.equal(classify(yes, { ...paid, totalCrc: 1 }).kind, "pending");
  assert.equal(classify(yes, { ...paid, status: "APPROVED" }).kind, "pending");
  assert.equal(classify({ ...yes, paymentMode: "REAL" }, paid).kind, "pending");
  assert.equal(classify(null, paid).kind, "pending");
  assert.equal(classify(yes, null).kind, "pending");
  assert.equal(classifyPaymentResult(yes, paid, 27, 99).kind, "pending");
});

test("BKL-039: identificadores URL no admiten inyección ni resultados", () => {
  for (const raw of ["0", "-1", "27?status=approved", "1e3", "2.7",
    "9999999999999999999999999999", "undefined", "", "27/aprobado"]) {
    assert.equal(parseResultId(raw), null, raw);
  }
  assert.equal(parseResultId("27"), 27);
  assert.equal(parseResultId(null), null);
});
