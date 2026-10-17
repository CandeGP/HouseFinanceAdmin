"use client";

import { useState } from "react";
import { useFinance } from "@/components/Shell";
import { Card, CardChip, PageTitle, Tag, fieldClass } from "@/components/ui";
import { EditTxDialog } from "@/components/EditTxDialog";
import type { Tx } from "@/lib/data";
import { supabase } from "@/lib/supabase";
import { dayLabel, monthKey, monthLabel, mxn } from "@/lib/format";

type StatusFilter = "all" | "pending_review" | "confirmed";

export default function Gastos() {
  const { finance, uid, scope, reload } = useFinance();
  const [editing, setEditing] = useState<Tx | null>(null);
  const [suggesting, setSuggesting] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  async function suggestCategories() {
    const household = finance.members.find((m) => m.user_id === uid)?.household_id;
    if (!household) return;
    setSuggesting(true);
    setNotice(null);
    const { data, error } = await supabase.rpc("apply_category_suggestions", { p_household: household });
    setSuggesting(false);
    if (error) return setNotice("No se pudo categorizar. Intenta de nuevo.");
    setNotice(`${data} gasto(s) categorizados`);
    reload();
  }
  const [month, setMonth] = useState("all");
  const [status, setStatus] = useState<StatusFilter>("all");
  const [query, setQuery] = useState("");

  const txs = finance.txs.filter((t) => scope === "both" || t.owner_id === uid);
  // Mensualidades de compras a meses que se pagan en cada mes (también los futuros, que no tienen gastos).
  const myCards = new Set(finance.cards.filter((c) => c.owner_id === uid).map((c) => c.id));
  const scheduled = finance.msi.filter((m) => scope === "both" || myCards.has(m.card_id));
  const months = [...new Set([...txs.map((t) => monthKey(t.occurred_at)), ...scheduled.map((m) => monthKey(m.due_date))])]
    .sort()
    .reverse();
  const monthScheduled =
    month === "all" ? [] : scheduled.filter((m) => monthKey(m.due_date) === month).sort((a, b) => b.amount - a.amount);
  const needle = query.trim().toLowerCase();

  const rows = txs.filter(
    (t) =>
      (month === "all" || monthKey(t.occurred_at) === month) &&
      (status === "all" || t.status === status) &&
      (needle === "" || t.merchant.toLowerCase().includes(needle)),
  );
  const total = rows.reduce((sum, t) => sum + t.amount, 0);

  const cardOf = (id: string | null) => finance.cards.find((c) => c.id === id);
  const categoryLabel = (id: string | null) => finance.categories.find((c) => c.id === id)?.name ?? "Sin categoría";
  const ownerLabel = (id: string) => finance.members.find((m) => m.user_id === id)?.display_name ?? "—";

  return (
    <>
      <PageTitle title="Gastos" subtitle={`${rows.length} movimientos · ${mxn(total)}`} />

      <div className="mb-6 grid gap-3 sm:grid-cols-3">
        <input className={fieldClass} placeholder="Buscar comercio" value={query} onChange={(e) => setQuery(e.target.value)} />
        <select className={fieldClass} value={month} onChange={(e) => setMonth(e.target.value)}>
          <option value="all">Todos los meses</option>
          {months.map((m) => <option key={m} value={m}>{monthLabel(m)}</option>)}
        </select>
        <select className={fieldClass} value={status} onChange={(e) => setStatus(e.target.value as StatusFilter)}>
          <option value="all">Todos los estados</option>
          <option value="confirmed">Confirmados</option>
          <option value="pending_review">Por revisar</option>
        </select>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <button onClick={suggestCategories} disabled={suggesting}
          className="rounded-full border border-steel px-4 py-2 text-sm font-semibold text-steel disabled:opacity-50">
          {suggesting ? "Categorizando…" : "Categorizar los que faltan"}
        </button>
        {notice ? <span className="text-sm text-muted">{notice}</span> : null}
      </div>

      {monthScheduled.length > 0 ? (
        <Card className="mb-6 p-0">
          <div className="flex items-center justify-between gap-4 px-6 py-4">
            <div>
              <p className="font-semibold">Mensualidades programadas (compras a meses)</p>
              <p className="text-sm text-muted">{monthScheduled.length} mensualidades · no cuentan como gasto nuevo</p>
            </div>
            <p className="text-lg font-bold tracking-[-0.03em]">{mxn(monthScheduled.reduce((s, m) => s + m.amount, 0))}</p>
          </div>
          <div className="divide-y divide-line border-t border-line">
            {monthScheduled.map((m) => (
              <div key={`${m.transaction_id}-${m.due_date}`} className="flex items-center justify-between gap-4 px-6 py-3">
                <div className="min-w-0">
                  <p className="truncate font-semibold">{m.merchant}</p>
                  <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted">
                    <span>Vence {dayLabel(m.due_date)}</span>
                    {cardOf(m.card_id) ? <CardChip card={cardOf(m.card_id)!} /> : null}
                    <Tag>
                      MSI {m.installment_number}/{m.installments}
                      {m.installment_number >= m.installments ? " · última" : ""}
                    </Tag>
                  </div>
                </div>
                <p className="font-semibold tracking-[-0.03em]">{mxn(m.amount)}</p>
              </div>
            ))}
          </div>
        </Card>
      ) : null}

      <Card className="divide-y divide-line p-0">
        {rows.map((t) => (
          <div key={t.id} onClick={() => (t.owner_id === uid ? setEditing(t) : undefined)}
            className={`grid grid-cols-[1fr_auto] gap-x-4 gap-y-2 px-6 py-4 md:grid-cols-[90px_1fr_220px_160px_auto] ${t.owner_id === uid ? "cursor-pointer hover:bg-canvas" : ""}`}>
            <p className="text-sm text-muted md:self-center">{dayLabel(t.occurred_at)}</p>
            <div className="min-w-0 md:self-center">
              <p className="truncate font-semibold">{t.merchant}</p>
              <p className="text-sm text-muted">{ownerLabel(t.owner_id)}</p>
            </div>
            <div className="hidden md:block md:justify-self-start md:self-center">
              {cardOf(t.card_id) ? <CardChip card={cardOf(t.card_id)!} /> : <span className="text-sm text-muted">Sin tarjeta</span>}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Tag>{categoryLabel(t.category_id)}</Tag>
              {t.payment_mode === "msi" ? <Tag>MSI {t.installment_number ?? 0}/{t.installments}</Tag> : null}
              {t.status === "pending_review" ? <Tag>Por revisar</Tag> : null}
            </div>
            <p className="text-right font-semibold tracking-[-0.03em] md:self-center">−{mxn(t.amount)}</p>
          </div>
        ))}
        {rows.length === 0 ? <p className="p-6 text-muted">Nada coincide con los filtros.</p> : null}
      </Card>

      {editing ? (
        <EditTxDialog
          tx={editing}
          categories={finance.categories}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            reload();
          }}
        />
      ) : null}
    </>
  );
}
