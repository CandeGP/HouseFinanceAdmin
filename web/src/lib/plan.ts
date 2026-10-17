// Lógica de pagos y vencimientos: mismo comportamiento que la app Android (NextPayments, MsiProjection y
// BalanceCheck). Funciones puras sobre fechas ISO "YYYY-MM-DD" para poder probarlas sin navegador.
import type { Estimate, MsiInstallment, PayStatus } from "./data";

/** Último día de la primera quincena: lo que vence del 1 al 15 va en la 1ª, el resto en la 2ª. */
export const QUINCENA_SPLIT_DAY = 15;

const pad = (n: number): string => String(n).padStart(2, "0");
const parts = (iso: string): [number, number, number] => {
  const [y, m, d] = iso.split("-").map(Number);
  return [y, m, d];
};
const lastDay = (y: number, m: number): number => new Date(Date.UTC(y, m, 0)).getUTCDate();
const round2 = (n: number): number => Math.round(n * 100) / 100;

/** Días de [from] a [to] (negativo si [to] ya pasó). */
export function daysBetween(from: string, to: string): number {
  const [fy, fm, fd] = parts(from);
  const [ty, tm, td] = parts(to);
  return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86_400_000);
}

const UNPAID_STATES = new Set(["pending", "overdue", "partial", "minimum_only"]);

/** Lo que aún se debe del último corte cerrado; 0 si está pagado o si no se lleva registro de pagos. */
export const unpaidAmount = (status?: PayStatus): number =>
  status && UNPAID_STATES.has(status.status) ? Math.max(0, status.remaining) : 0;

/** Corte anterior sin pagar + ciclo abierto + mensualidades que faltan: lo que se compara con el banco. */
export const debtTotal = (estimate?: Estimate, status?: PayStatus): number =>
  unpaidAmount(status) + (estimate?.cycle_total ?? 0) + (estimate?.msi_remaining ?? 0);

export interface Obligation {
  amount: number;
  dueDate: string;
  fromClosedCycle: boolean;
  overdue: boolean;
  paidClosedCycle: boolean;
  daysUntilDue: number;
}

/** Todo lo que se debe pagar de una tarjeta: el corte cerrado sin pagar (si lo hay) y el ciclo abierto. */
export function paymentObligations(estimate: Estimate | undefined, status: PayStatus | undefined, today: string): Obligation[] {
  const out: Obligation[] = [];
  const unpaid = unpaidAmount(status);
  if (status && unpaid > 0) {
    out.push({
      amount: unpaid,
      dueDate: status.due_date,
      fromClosedCycle: true,
      overdue: status.due_date < today,
      paidClosedCycle: false,
      daysUntilDue: daysBetween(today, status.due_date),
    });
  }
  if (estimate) {
    out.push({
      amount: estimate.cycle_total,
      dueDate: estimate.due_date,
      fromClosedCycle: false,
      overdue: false,
      paidClosedCycle: status?.status === "paid" && status.target_amount > 0,
      daysUntilDue: daysBetween(today, estimate.due_date),
    });
  }
  return out;
}

/** Lo que toca pagar primero: el corte cerrado sin pagar y, si no hay, el ciclo abierto. */
export const nextPayment = (estimate: Estimate | undefined, status: PayStatus | undefined, today: string): Obligation | undefined =>
  paymentObligations(estimate, status, today)[0];

/** "hoy", "mañana", "ayer", "en 5 días" o "hace 3 días". */
export function relativeDays(days: number): string {
  if (days === 0) return "hoy";
  if (days === 1) return "mañana";
  if (days === -1) return "ayer";
  return days > 1 ? `en ${days} días` : `hace ${-days} días`;
}

/** Una sola línea con la fecha y cuánto falta, sin repetir montos. [dayLabel] da "26 oct". */
export function dueLine(o: Obligation, dayLabel: (iso: string) => string): string {
  const due = dayLabel(o.dueDate);
  const when = relativeDays(o.daysUntilDue);
  if (o.overdue) return `Vencido el ${due} (${when})`;
  if (o.fromClosedCycle) return `Vence ${due} (${when})`;
  if (o.paidClosedCycle) return `Pagado ✓ · siguiente pago vence ${due} (${when})`;
  return `Siguiente pago vence ${due} (${when})`;
}

export interface Quincena { total: number; cards: number; overdue: number }
export interface QuincenaTotals { first: Quincena; second: Quincena; nextFirstEnd: string | null }

const quincenaOf = (list: Obligation[]): Quincena => ({
  total: round2(list.reduce((s, o) => s + o.amount, 0)),
  cards: list.length,
  overdue: round2(list.filter((o) => o.overdue).reduce((s, o) => s + o.amount, 0)),
});

/**
 * Suma lo que vence por quincena. Del 1 al 15: la 1ª y la 2ª de este mes. Pasado el 15, la 1ª ya no se puede
 * pagar a tiempo: el primer bloque pasa a ser la 1ª quincena del mes que viene y el segundo junta todo lo que
 * falta de este mes (incluido lo vencido). Los pagos de meses posteriores no cuentan.
 */
export function quincenaTotals(obligations: Obligation[], today: string): QuincenaTotals {
  const [y, m, d] = today.split("-").map(Number);
  const monthEnd = `${y}-${pad(m)}-${pad(lastDay(y, m))}`;
  const owed = obligations.filter((o) => o.amount > 0);
  const dayOf = (iso: string): number => parts(iso)[2];

  if (d <= QUINCENA_SPLIT_DAY) {
    const due = owed.filter((o) => o.dueDate <= monthEnd);
    return {
      first: quincenaOf(due.filter((o) => dayOf(o.dueDate) <= QUINCENA_SPLIT_DAY)),
      second: quincenaOf(due.filter((o) => dayOf(o.dueDate) > QUINCENA_SPLIT_DAY)),
      nextFirstEnd: null,
    };
  }
  const ny = m === 12 ? y + 1 : y;
  const nm = m === 12 ? 1 : m + 1;
  const nextStart = `${ny}-${pad(nm)}-01`;
  const nextFirstEnd = `${ny}-${pad(nm)}-${pad(QUINCENA_SPLIT_DAY)}`;
  return {
    first: quincenaOf(owed.filter((o) => o.dueDate >= nextStart && o.dueDate <= nextFirstEnd)),
    second: quincenaOf(owed.filter((o) => o.dueDate <= monthEnd)),
    nextFirstEnd,
  };
}

export interface MsiMonth {
  key: string; // "2026-11"
  items: MsiInstallment[];
  total: number;
  finishing: number;
}

/** Agrupa las mensualidades por mes de pago, en orden; dentro de cada mes, primero las más grandes. */
export function projectInstallments(rows: MsiInstallment[]): MsiMonth[] {
  const byMonth = new Map<string, MsiInstallment[]>();
  for (const row of rows) {
    const key = row.due_date.slice(0, 7);
    byMonth.set(key, [...(byMonth.get(key) ?? []), row]);
  }
  return [...byMonth.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, items]) => {
      const sorted = [...items].sort((a, b) => b.amount - a.amount);
      return {
        key,
        items: sorted,
        total: round2(sorted.reduce((s, i) => s + i.amount, 0)),
        finishing: sorted.filter((i) => i.installment_number >= i.installments).length,
      };
    });
}

export interface MsiPending { installments: number; amount: number; lastMonth: string | null }

export const pendingInstallments = (months: MsiMonth[]): MsiPending => ({
  installments: months.reduce((s, m) => s + m.items.length, 0),
  amount: round2(months.reduce((s, m) => s + m.total, 0)),
  lastMonth: months.length ? months[months.length - 1].key : null,
});

/** Pesos que se perdonan siempre (redondeos), o el 0.5 % del saldo si es mayor. */
export const BALANCE_TOLERANCE_MIN = 10;
export const BALANCE_TOLERANCE_RATIO = 0.005;

export type BalanceVerdict = "match" | "bank_higher" | "app_higher";

export interface BalanceComparison { bank: number; app: number; diff: number; verdict: BalanceVerdict }

export function compareBalance(app: number, bank: number): BalanceComparison {
  const diff = round2(bank - app);
  const tolerance = Math.max(BALANCE_TOLERANCE_MIN, BALANCE_TOLERANCE_RATIO * bank);
  const verdict: BalanceVerdict = Math.abs(diff) <= tolerance ? "match" : diff > 0 ? "bank_higher" : "app_higher";
  return { bank, app, diff, verdict };
}

export function balanceMessage(c: BalanceComparison, money: (n: number) => string): string {
  switch (c.verdict) {
    case "match":
      return `Cuadra con el banco (diferencia de ${money(Math.abs(c.diff))}).`;
    case "bank_higher":
      return `El banco debe ${money(c.diff)} más que la app: falta registrar cargos o compras a meses.`;
    default:
      return `La app suma ${money(-c.diff)} de más: puede haber un cargo duplicado o una devolución sin descontar.`;
  }
}
