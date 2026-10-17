import { supabase } from "./supabase";

export interface Member { user_id: string; display_name: string; household_id: string }
export interface Category { id: string; name: string }

export interface Card {
  id: string;
  owner_id: string;
  name: string;
  bank: string;
  last4: string;
  color: string | null;
  monthly_budget: number | null;
  credit_limit: number;
  cutoff_day: number;
  due_day: number;
}

export interface Tx {
  id: string;
  owner_id: string;
  card_id: string | null;
  category_id: string | null;
  merchant: string;
  amount: number;
  occurred_at: string;
  payment_mode: "contado" | "msi";
  installments: number | null;
  installment_number: number | null;
  status: "pending_review" | "confirmed";
  source: "manual" | "notification" | "email" | "statement";
}

/** Resultado de la función SQL card_cycle_estimate para una tarjeta. */
export interface Estimate {
  cycle_start: string;
  cycle_end: string;
  due_date: string;
  cycle_contado_total: number;
  cycle_msi_total: number;
  cycle_total: number;
  msi_remaining: number;
  credit_limit: number;
  usage_pct: number;
}

/** Gasto del mes calendario de una tarjeta frente a su presupuesto (función SQL card_month_spend). */
export interface MonthSpend {
  spent: number;
  budget: number | null;
  pct: number | null;
}

export type PayState = "paid" | "minimum_only" | "partial" | "pending" | "overdue" | "untracked";

/** Estado de pago del último ciclo cerrado de una tarjeta (función SQL card_cycle_status). */
export interface PayStatus {
  cutoff_date: string;
  due_date: string;
  target_amount: number;
  target_source: "statement" | "estimate";
  minimum_payment: number | null;
  paid_amount: number;
  remaining: number;
  status: PayState;
}

/** Una mensualidad futura de una compra a meses (función SQL msi_schedule). */
export interface MsiInstallment {
  card_id: string;
  transaction_id: string;
  merchant: string;
  cycle_end: string;
  due_date: string;
  installment_number: number;
  installments: number;
  amount: number;
}

/** Saldo utilizado que mostraba el banco en `checked_on`, capturado desde la app. */
export interface BalanceCheck {
  card_id: string;
  bank_balance: number;
  checked_on: string;
}

export interface Finance {
  members: Member[];
  cards: Card[];
  categories: Category[];
  txs: Tx[];
  estimates: Record<string, Estimate>;
  monthSpend: Record<string, MonthSpend>;
  payStatus: Record<string, PayStatus>;
  /** Mensualidades que faltan, ciclo por ciclo; vacío si el servidor aún no tiene la función. */
  msi: MsiInstallment[];
  /** Última captura del saldo del banco por tarjeta. */
  balanceChecks: Record<string, BalanceCheck>;
}

/** Fila de la función SQL card_snapshots: estimación del ciclo y estado de pago (prefijo st_) en una llamada. */
interface SnapshotRow extends Partial<Estimate> {
  card_id: string;
  st_cutoff_date: string | null;
  st_due_date: string | null;
  st_target_amount: number | null;
  st_target_source: "statement" | "estimate" | null;
  st_minimum_payment: number | null;
  st_paid_amount: number | null;
  st_remaining: number | null;
  st_status: PayState | null;
}

const MSI_HORIZON_MONTHS = 60;

const MONTHS_BACK = 6;
const TX_LIMIT = 1000;

const isoDate = (d: Date): string => d.toISOString().slice(0, 10);

/** Carga todo lo que el dashboard muestra. RLS limita las filas al hogar de la persona. */
export async function loadFinance(): Promise<Finance> {
  const since = new Date();
  since.setMonth(since.getMonth() - MONTHS_BACK);

  const [members, cards, categories, txs] = await Promise.all([
    supabase.from("members").select("user_id,display_name,household_id"),
    supabase
      .from("cards")
      .select("id,owner_id,name,bank,last4,color,monthly_budget,credit_limit,cutoff_day,due_day")
      .eq("archived", false),
    supabase.from("categories").select("id,name"),
    supabase
      .from("transactions")
      .select(
        "id,owner_id,card_id,category_id,merchant,amount,occurred_at,payment_mode,installments,installment_number,status,source",
      )
      .gte("occurred_at", isoDate(since))
      .order("occurred_at", { ascending: false })
      .limit(TX_LIMIT),
  ]);
  for (const result of [members, cards, categories, txs]) {
    if (result.error) throw new Error(result.error.message);
  }

  const cardRows = (cards.data ?? []) as Card[];

  // Una sola consulta trae la estimación y el estado de pago de todas las tarjetas; si la función aún no
  // existe en el servidor, se cae a una consulta por tarjeta.
  const snapshots = await loadSnapshots(cardRows);

  // Calendario de compras a meses y saldos del banco: si fallan, el resto de la página sigue funcionando.
  const [msi, checks] = await Promise.all([loadMsi(), loadBalanceChecks()]);

  // Solo las tarjetas con presupuesto necesitan el gasto del mes.
  const spendPairs = await Promise.all(
    cardRows
      .filter((card) => card.monthly_budget !== null)
      .map(async (card) => {
        const { data, error } = await supabase.rpc("card_month_spend", { p_card: card.id });
        if (error) throw new Error(error.message);
        const row = (data as MonthSpend[] | null)?.[0];
        return row ? ([card.id, row] as const) : null;
      }),
  );

  return {
    members: (members.data ?? []) as Member[],
    cards: cardRows,
    categories: (categories.data ?? []) as Category[],
    txs: (txs.data ?? []) as Tx[],
    estimates: snapshots.estimates,
    monthSpend: Object.fromEntries(spendPairs.filter((p) => p !== null)),
    payStatus: snapshots.payStatus,
    msi,
    balanceChecks: checks,
  };
}

interface Snapshots { estimates: Record<string, Estimate>; payStatus: Record<string, PayStatus> }

async function loadSnapshots(cards: Card[]): Promise<Snapshots> {
  const { data, error } = await supabase.rpc("card_snapshots");
  if (!error) {
    const ids = new Set(cards.map((c) => c.id));
    const estimates: Record<string, Estimate> = {};
    const payStatus: Record<string, PayStatus> = {};
    for (const row of (data ?? []) as SnapshotRow[]) {
      if (!ids.has(row.card_id)) continue;
      if (row.cycle_start && row.cycle_end && row.due_date) estimates[row.card_id] = row as Estimate;
      if (row.st_cutoff_date && row.st_due_date && row.st_status) {
        payStatus[row.card_id] = {
          cutoff_date: row.st_cutoff_date,
          due_date: row.st_due_date,
          target_amount: row.st_target_amount ?? 0,
          target_source: row.st_target_source ?? "estimate",
          minimum_payment: row.st_minimum_payment,
          paid_amount: row.st_paid_amount ?? 0,
          remaining: row.st_remaining ?? 0,
          status: row.st_status,
        };
      }
    }
    return { estimates, payStatus };
  }

  const estimatePairs = await Promise.all(
    cards.map(async (card) => {
      const { data: est, error: e } = await supabase.rpc("card_cycle_estimate", { p_card_id: card.id });
      if (e) throw new Error(e.message);
      const row = (est as Estimate[] | null)?.[0];
      return row ? ([card.id, row] as const) : null;
    }),
  );
  const statusPairs = await Promise.all(
    cards.map(async (card) => {
      const { data: st, error: e } = await supabase.rpc("card_cycle_status", { p_card: card.id });
      if (e) throw new Error(e.message);
      const row = (st as PayStatus[] | null)?.[0];
      return row ? ([card.id, row] as const) : null;
    }),
  );
  return {
    estimates: Object.fromEntries(estimatePairs.filter((p) => p !== null)),
    payStatus: Object.fromEntries(statusPairs.filter((p) => p !== null)),
  };
}

async function loadMsi(): Promise<MsiInstallment[]> {
  const { data, error } = await supabase.rpc("msi_schedule", { p_months: MSI_HORIZON_MONTHS });
  return error ? [] : ((data ?? []) as MsiInstallment[]);
}

async function loadBalanceChecks(): Promise<Record<string, BalanceCheck>> {
  const { data, error } = await supabase
    .from("card_balance_checks")
    .select("card_id,bank_balance,checked_on")
    .order("checked_on", { ascending: false })
    .order("created_at", { ascending: false });
  if (error) return {};
  const latest: Record<string, BalanceCheck> = {};
  for (const row of (data ?? []) as BalanceCheck[]) if (!latest[row.card_id]) latest[row.card_id] = row;
  return latest;
}
