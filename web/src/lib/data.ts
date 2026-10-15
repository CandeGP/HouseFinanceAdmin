import { supabase } from "./supabase";

export interface Member { user_id: string; display_name: string }
export interface Category { id: string; name: string }

export interface Card {
  id: string;
  owner_id: string;
  name: string;
  bank: string;
  last4: string;
  color: string | null;
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

export interface Finance {
  members: Member[];
  cards: Card[];
  categories: Category[];
  txs: Tx[];
  estimates: Record<string, Estimate>;
}

const MONTHS_BACK = 6;
const TX_LIMIT = 1000;

const isoDate = (d: Date): string => d.toISOString().slice(0, 10);

/** Carga todo lo que el dashboard muestra. RLS limita las filas al hogar de la persona. */
export async function loadFinance(): Promise<Finance> {
  const since = new Date();
  since.setMonth(since.getMonth() - MONTHS_BACK);

  const [members, cards, categories, txs] = await Promise.all([
    supabase.from("members").select("user_id,display_name"),
    supabase
      .from("cards")
      .select("id,owner_id,name,bank,last4,color,credit_limit,cutoff_day,due_day")
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
  const pairs = await Promise.all(
    cardRows.map(async (card) => {
      const { data, error } = await supabase.rpc("card_cycle_estimate", { p_card_id: card.id });
      if (error) throw new Error(error.message);
      const row = (data as Estimate[] | null)?.[0];
      return row ? ([card.id, row] as const) : null;
    }),
  );

  return {
    members: (members.data ?? []) as Member[],
    cards: cardRows,
    categories: (categories.data ?? []) as Category[],
    txs: (txs.data ?? []) as Tx[],
    estimates: Object.fromEntries(pairs.filter((p) => p !== null)),
  };
}
