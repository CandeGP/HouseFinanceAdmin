"use client";

import { useFinance } from "@/components/Shell";
import { Bar, Card, PageTitle, Tag } from "@/components/ui";
import { cardAccent, cardLabel } from "@/lib/cards";
import { dayLabel, mxn } from "@/lib/format";

export default function Tarjetas() {
  const { finance, uid, scope } = useFinance();
  const cards = finance.cards.filter((c) => scope === "both" || c.owner_id === uid);
  const ownerLabel = (id: string) => finance.members.find((m) => m.user_id === id)?.display_name ?? "—";

  return (
    <>
      <PageTitle title="Tarjetas" subtitle="Ciclo actual, fecha de pago y uso del límite." />

      <section className="grid gap-6 md:grid-cols-2">
        {cards.map((card) => {
          const est = finance.estimates[card.id];
          return (
            <Card key={card.id} style={{ borderTop: `6px solid ${cardAccent(card)}` }}>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-semibold tracking-[-0.03em]">{card.name}</h2>
                  <p className="text-muted">{cardLabel(card)}</p>
                </div>
                <Tag>{ownerLabel(card.owner_id)}</Tag>
              </div>

              {card.monthly_budget !== null ? (
                <div className="mt-6">
                  {(() => {
                    const spent = finance.monthSpend[card.id]?.spent ?? 0;
                    const over = spent > card.monthly_budget;
                    return (
                      <>
                        <div className="mb-1 flex justify-between text-sm">
                          <span className="font-semibold">Presupuesto del mes</span>
                          <span className={over ? "font-semibold text-red-600 dark:text-red-400" : "text-muted"}>
                            {mxn(spent)} de {mxn(card.monthly_budget)}
                          </span>
                        </div>
                        <Bar pct={(spent / card.monthly_budget) * 100} />
                        {over ? (
                          <p className="mt-1 text-sm text-red-600 dark:text-red-400">
                            Te pasaste por {mxn(spent - card.monthly_budget)}
                          </p>
                        ) : null}
                      </>
                    );
                  })()}
                </div>
              ) : null}

              {est ? (
                <>
                  <div className="mt-6">
                    <div className="mb-1 flex justify-between text-sm">
                      <span className="font-semibold">Uso del límite</span>
                      <span className="text-muted">{Math.round(est.usage_pct)}% de {mxn(est.credit_limit)}</span>
                    </div>
                    <Bar pct={est.usage_pct} />
                  </div>
                  <dl className="mt-6 grid grid-cols-2 gap-y-3 text-sm">
                    <dt className="text-muted">Ciclo</dt>
                    <dd className="text-right font-semibold">{dayLabel(est.cycle_start)} – {dayLabel(est.cycle_end)}</dd>
                    <dt className="text-muted">Fecha de pago</dt>
                    <dd className="text-right font-semibold">{dayLabel(est.due_date)}</dd>
                    <dt className="text-muted">Contado</dt>
                    <dd className="text-right font-semibold">{mxn(est.cycle_contado_total)}</dd>
                    <dt className="text-muted">Mensualidades MSI</dt>
                    <dd className="text-right font-semibold">{mxn(est.cycle_msi_total)}</dd>
                    <dt className="text-muted">Total del ciclo</dt>
                    <dd className="text-right text-lg font-semibold tracking-[-0.03em]">{mxn(est.cycle_total)}</dd>
                    <dt className="text-muted">MSI restante</dt>
                    <dd className="text-right font-semibold">{mxn(est.msi_remaining)}</dd>
                  </dl>
                </>
              ) : (
                <p className="mt-6 text-muted">Sin ciclo calculado todavía.</p>
              )}
            </Card>
          );
        })}
        {cards.length === 0 ? <Card><p className="text-muted">No hay tarjetas registradas.</p></Card> : null}
      </section>
    </>
  );
}
