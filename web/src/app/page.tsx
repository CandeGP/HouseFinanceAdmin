"use client";

import { useFinance } from "@/components/Shell";
import { Bar, Card, CardChip, PageTitle, Stat, Tag } from "@/components/ui";
import { cardAccent, cardLabel } from "@/lib/cards";
import { currentMonth, dayLabel, monthKey, monthLabel, mxn } from "@/lib/format";

const TOP_CATEGORIES = 5;
const RECENT = 8;

export default function Resumen() {
  const { finance, uid, scope } = useFinance();
  const visible = (owner: string) => scope === "both" || owner === uid;

  const month = currentMonth();
  const txs = finance.txs.filter((t) => visible(t.owner_id));
  const cards = finance.cards.filter((c) => visible(c.owner_id));
  const monthTxs = txs.filter((t) => t.status === "confirmed" && monthKey(t.occurred_at) === month);

  const monthTotal = monthTxs.reduce((sum, t) => sum + t.amount, 0);
  const pending = txs.filter((t) => t.status === "pending_review").length;
  const estimates = cards.flatMap((c) => (finance.estimates[c.id] ? [{ card: c, est: finance.estimates[c.id] }] : []));
  const toPay = estimates.reduce((sum, e) => sum + e.est.cycle_total, 0);
  const msiLeft = estimates.reduce((sum, e) => sum + e.est.msi_remaining, 0);
  const nextDue = [...estimates].sort((a, b) => a.est.due_date.localeCompare(b.est.due_date))[0];

  const categoryName = (id: string | null) => finance.categories.find((c) => c.id === id)?.name ?? "Sin categoría";
  const cardOf = (id: string | null) => finance.cards.find((c) => c.id === id);
  const byCategory = Object.entries(
    monthTxs.reduce<Record<string, number>>((acc, t) => {
      const name = categoryName(t.category_id);
      return { ...acc, [name]: (acc[name] ?? 0) + t.amount };
    }, {}),
  )
    .sort((a, b) => b[1] - a[1])
    .slice(0, TOP_CATEGORIES);
  const maxCategory = byCategory[0]?.[1] ?? 1;

  return (
    <>
      <PageTitle title="Tu mes en una vista" subtitle={`${monthLabel(month)} · ${scope === "both" ? "los dos" : "solo lo tuyo"}`} />

      <section className="grid gap-6 md:grid-cols-4">
        <Stat label="Gastado este mes" value={mxn(monthTotal)} hint={`${monthTxs.length} movimientos`} />
        <Stat label="Por pagar (ciclo)" value={mxn(toPay)} hint="suma de tus tarjetas" />
        <Stat label="MSI pendiente" value={mxn(msiLeft)} />
        <Stat label="Por revisar" value={String(pending)} hint="capturas sin confirmar" />
      </section>

      <section className="mt-12 grid gap-6 lg:grid-cols-3">
        {nextDue ? (
          <div className="relative overflow-hidden rounded-xl bg-voltage p-6 text-on-voltage shadow-card">
            <div className="absolute -right-10 -top-10 size-40 rounded-full bg-on-voltage/5" />
            <div className="absolute -bottom-12 right-6 size-28 rounded-full bg-on-voltage/5" />
            <p className="relative text-[10px] font-semibold uppercase tracking-[0.04em]">Próximo pago</p>
            <p className="relative mt-3 text-4xl font-bold tracking-[-0.05em]">{dayLabel(nextDue.est.due_date)}</p>
            <p className="relative mt-3 inline-flex items-center gap-2 rounded-full bg-on-voltage/10 px-3 py-1.5 text-sm font-semibold">
              <span className="size-2.5 rounded-full" style={{ background: cardAccent(nextDue.card) }} />
              {cardLabel(nextDue.card)}
            </p>
            <p className="relative mt-5 text-3xl font-semibold tracking-[-0.04em]">{mxn(nextDue.est.cycle_total)}</p>
          </div>
        ) : (
          <Card><p className="text-muted">Aún no hay tarjetas con ciclo calculado.</p></Card>
        )}

        <Card className="lg:col-span-2">
          <h2 className="text-2xl font-semibold tracking-[-0.03em]">Por categoría</h2>
          <div className="mt-4 flex flex-col gap-4">
            {byCategory.length === 0 ? <p className="text-muted">Sin gastos confirmados este mes.</p> : null}
            {byCategory.map(([name, total]) => (
              <div key={name}>
                <div className="mb-1 flex justify-between text-sm">
                  <span className="font-semibold">{name}</span>
                  <span className="text-muted">{mxn(total)}</span>
                </div>
                <Bar pct={(total / maxCategory) * 100} />
              </div>
            ))}
          </div>
        </Card>
      </section>

      <section className="mt-12">
        <h2 className="mb-4 text-2xl font-semibold tracking-[-0.03em]">Últimos movimientos</h2>
        <Card className="divide-y divide-line p-0">
          {txs.slice(0, RECENT).map((t) => (
            <div key={t.id} className="flex items-center justify-between gap-4 px-6 py-4">
              <div className="min-w-0">
                <p className="truncate font-semibold">{t.merchant}</p>
                <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted">
                  <span>{dayLabel(t.occurred_at)}</span>
                  {cardOf(t.card_id) ? <CardChip card={cardOf(t.card_id)!} /> : null}
                  <Tag>{categoryName(t.category_id)}</Tag>
                </div>
              </div>
              <div className="flex items-center gap-3">
                {t.status === "pending_review" ? <Tag>Por revisar</Tag> : null}
                <span className="font-semibold tracking-[-0.03em]">−{mxn(t.amount)}</span>
              </div>
            </div>
          ))}
          {txs.length === 0 ? <p className="p-6 text-muted">Todavía no hay movimientos.</p> : null}
        </Card>
      </section>
    </>
  );
}
