"use client";

import { useState } from "react";
import { useFinance } from "@/components/Shell";
import { Card, PageTitle, Tag, fieldClass } from "@/components/ui";
import { dayLabel, monthKey, monthLabel, mxn } from "@/lib/format";

type StatusFilter = "all" | "pending_review" | "confirmed";

export default function Gastos() {
  const { finance, uid, scope } = useFinance();
  const [month, setMonth] = useState("all");
  const [status, setStatus] = useState<StatusFilter>("all");
  const [query, setQuery] = useState("");

  const txs = finance.txs.filter((t) => scope === "both" || t.owner_id === uid);
  const months = [...new Set(txs.map((t) => monthKey(t.occurred_at)))].sort().reverse();
  const needle = query.trim().toLowerCase();

  const rows = txs.filter(
    (t) =>
      (month === "all" || monthKey(t.occurred_at) === month) &&
      (status === "all" || t.status === status) &&
      (needle === "" || t.merchant.toLowerCase().includes(needle)),
  );
  const total = rows.reduce((sum, t) => sum + t.amount, 0);

  const cardLabel = (id: string | null) => {
    const card = finance.cards.find((c) => c.id === id);
    return card ? `${card.name} ••${card.last4}` : "—";
  };
  const categoryLabel = (id: string | null) => finance.categories.find((c) => c.id === id)?.name ?? "—";
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

      <Card className="divide-y divide-line p-0">
        {rows.map((t) => (
          <div key={t.id} className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-2 px-6 py-4 md:grid-cols-[90px_1fr_180px_140px_auto]">
            <p className="text-sm text-muted md:self-center">{dayLabel(t.occurred_at)}</p>
            <div className="min-w-0 md:self-center">
              <p className="truncate font-semibold">{t.merchant}</p>
              <p className="text-sm text-muted">{ownerLabel(t.owner_id)}</p>
            </div>
            <p className="hidden text-sm text-muted md:block md:self-center">{cardLabel(t.card_id)}</p>
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
    </>
  );
}
