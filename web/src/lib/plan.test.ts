import assert from "node:assert/strict";
import { test } from "node:test";
import type { Estimate, MsiInstallment, PayStatus } from "./data.ts";
import {
  balanceMessage,
  compareBalance,
  daysBetween,
  debtTotal,
  dueLine,
  nextPayment,
  paymentObligations,
  pendingInstallments,
  projectInstallments,
  quincenaTotals,
  relativeDays,
  unpaidAmount,
  type Obligation,
} from "./plan.ts";

const today = "2026-10-16";
const label = (iso: string): string => `${Number(iso.slice(8))}/${iso.slice(5, 7)}`;
const money = (n: number): string => `$${n.toFixed(2)}`;

const estimate = (cycle_total: number, msi_remaining = 0, due_date = "2026-11-05"): Estimate => ({
  cycle_start: "2026-10-05", cycle_end: "2026-11-04", due_date, cycle_contado_total: cycle_total, cycle_msi_total: 0,
  cycle_total, msi_remaining, credit_limit: 10_000, usage_pct: 0,
});

const status = (state: PayStatus["status"], target = 1000, paid = 0, due_date = "2026-10-25"): PayStatus => ({
  cutoff_date: "2026-10-10", due_date, target_amount: target, target_source: "statement", minimum_payment: null,
  paid_amount: paid, remaining: Math.max(0, target - paid), status: state,
});

const due = (amount: number, dueDate: string, overdue = false): Obligation => ({
  amount, dueDate, fromClosedCycle: true, overdue, paidClosedCycle: false, daysUntilDue: daysBetween(today, dueDate),
});

test("los días entre fechas cruzan meses y años", () => {
  assert.equal(daysBetween("2026-10-16", "2026-10-25"), 9);
  assert.equal(daysBetween("2026-12-30", "2027-01-02"), 3);
  assert.equal(daysBetween("2026-10-16", "2026-10-13"), -3);
});

test("con el corte anterior sin pagar toca pagar lo que falta de ese corte", () => {
  const next = nextPayment(estimate(2278.48, 22438.42), status("pending", 2010.97), today)!;
  assert.equal(next.amount, 2010.97);
  assert.equal(next.fromClosedCycle, true);
  assert.equal(dueLine(next, label), "Vence 25/10 (en 9 días)");
});

test("un pago parcial muestra solo lo que falta y vencido lo dice", () => {
  assert.equal(unpaidAmount(status("partial", 1000, 400)), 600);
  const next = nextPayment(estimate(10), status("overdue", 500), "2026-10-30")!;
  assert.equal(next.overdue, true);
  assert.equal(dueLine(next, label), "Vencido el 25/10 (hace 5 días)");
});

test("con el corte pagado o sin registro toca el ciclo abierto", () => {
  const paid = nextPayment(estimate(2278.48), status("paid", 1000, 1000), today)!;
  assert.equal(paid.amount, 2278.48);
  assert.equal(dueLine(paid, label), "Pagado ✓ · siguiente pago vence 5/11 (en 20 días)");
  const untracked = nextPayment(estimate(2278.48), status("untracked", 1408), today)!;
  assert.equal(unpaidAmount(status("untracked", 1408)), 0);
  assert.equal(dueLine(untracked, label), "Siguiente pago vence 5/11 (en 20 días)");
});

test("la deuda suma el corte sin pagar, el ciclo y las mensualidades que faltan", () => {
  assert.equal(Math.round(debtTotal(estimate(2278.48, 22438.42), status("pending", 2010.97)) * 100) / 100, 26727.87);
  assert.equal(debtTotal(estimate(500, 200), undefined), 700);
  assert.equal(debtTotal(undefined, undefined), 0);
});

test("las obligaciones juntan el corte sin pagar y el ciclo abierto", () => {
  const list = paymentObligations(estimate(2278.48), status("pending", 2010.97), today);
  assert.deepEqual(list.map((o) => o.amount), [2010.97, 2278.48]);
});

test("del 1 al 15 separa las quincenas y el 15 es de la primera", () => {
  const t = quincenaTotals([due(100, "2026-10-13"), due(50, "2026-10-15"), due(300, "2026-10-16"), due(25, "2026-10-31")], "2026-10-10");
  assert.deepEqual([t.first.total, t.first.cards], [150, 2]);
  assert.deepEqual([t.second.total, t.second.cards], [325, 2]);
  assert.equal(t.nextFirstEnd, null);
});

test("pasado el 15 el primer bloque es la primera quincena del mes que viene", () => {
  const t = quincenaTotals(
    [due(700, "2026-11-12"), due(300, "2026-11-15"), due(900, "2026-11-26"), due(400, "2026-10-26")],
    today,
  );
  assert.equal(t.nextFirstEnd, "2026-11-15");
  assert.deepEqual([t.first.total, t.first.cards], [1000, 2]);
  assert.deepEqual([t.second.total, t.second.cards], [400, 1]);
});

test("pasado el 15 lo vencido sigue visible en el segundo bloque y diciembre pasa a enero", () => {
  const t = quincenaTotals([due(200, "2026-10-13", true), due(50, "2026-10-25")], today);
  assert.deepEqual([t.second.total, t.second.overdue], [250, 200]);
  const dec = quincenaTotals([due(120, "2027-01-10")], "2026-12-20");
  assert.equal(dec.nextFirstEnd, "2027-01-15");
  assert.equal(dec.first.total, 120);
});

const inst = (merchant: string, dueDate: string, n: number, total: number, amount: number): MsiInstallment => ({
  card_id: "c1", transaction_id: `t-${merchant}`, merchant, cycle_end: dueDate, due_date: dueDate,
  installment_number: n, installments: total, amount,
});

const rows = [
  inst("AOC", "2026-12-09", 6, 9, 948.61),
  inst("AOC", "2026-11-09", 5, 9, 948.61),
  inst("HUAWEI", "2026-11-09", 9, 12, 359.92),
  inst("ML", "2026-11-09", 10, 10, 189.5),
  inst("HUAWEI", "2026-12-09", 10, 12, 359.92),
];

test("las mensualidades se agrupan por mes de pago, en orden y con lo más grande primero", () => {
  const months = projectInstallments(rows);
  assert.deepEqual(months.map((m) => m.key), ["2026-11", "2026-12"]);
  assert.deepEqual(months[0].items.map((i) => i.merchant), ["AOC", "HUAWEI", "ML"]);
  assert.equal(months[0].total, 1498.03);
  assert.equal(months[0].finishing, 1);
  assert.equal(months[1].finishing, 0);
});

test("el pendiente suma todo y dice cuándo termina; sin compras no hay nada", () => {
  const pending = pendingInstallments(projectInstallments(rows));
  assert.equal(pending.installments, 5);
  assert.equal(pending.lastMonth, "2026-12");
  assert.deepEqual(pendingInstallments(projectInstallments([])), { installments: 0, amount: 0, lastMonth: null });
});

test("los meses cruzan de año sin desordenarse", () => {
  const months = projectInstallments([inst("A", "2027-01-09", 2, 3, 100), inst("A", "2026-12-09", 1, 3, 100)]);
  assert.deepEqual(months.map((m) => m.key), ["2026-12", "2027-01"]);
});

test("la conciliación perdona centavos y avisa de diferencias grandes", () => {
  assert.equal(compareBalance(4559.11, 4558.95).verdict, "match");
  assert.equal(compareBalance(100, 109).verdict, "match");
  assert.equal(compareBalance(100, 111).verdict, "bank_higher");
  assert.equal(compareBalance(11173.84, 11200).verdict, "match");
  const high = compareBalance(11173.84, 11311.06);
  assert.equal(high.verdict, "bank_higher");
  assert.match(balanceMessage(high, money), /\$137\.22/);
  const low = compareBalance(6804.07, 5000);
  assert.equal(low.verdict, "app_higher");
  assert.match(balanceMessage(low, money), /duplicado/);
});

test("los días se dicen como una persona", () => {
  assert.deepEqual([0, 1, -1, 7, -3].map(relativeDays), ["hoy", "mañana", "ayer", "en 7 días", "hace 3 días"]);
});
