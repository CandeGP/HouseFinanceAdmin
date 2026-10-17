"use client";

import { useFinance } from "@/components/Shell";
import { Card, CardChip, PageTitle, Stat, Tag } from "@/components/ui";
import { dayLabel, monthLabel, mxn } from "@/lib/format";
import { pendingInstallments, projectInstallments } from "@/lib/plan";

/** Calendario de compras a meses: lo que se paga de mensualidades en cada mes que viene. */
export default function Meses() {
  const { finance, uid, scope } = useFinance();
  const myCards = new Set(finance.cards.filter((c) => c.owner_id === uid).map((c) => c.id));
  const rows = finance.msi.filter((m) => scope === "both" || myCards.has(m.card_id));
  const months = projectInstallments(rows);
  const pending = pendingInstallments(months);
  const cardOf = (id: string) => finance.cards.find((c) => c.id === id);

  return (
    <>
      <PageTitle
        title="Compras a meses"
        subtitle="Lo que pagarás de mensualidades en los próximos meses, de estados de cuenta y de capturas a mano."
      />

      <section className="grid gap-6 md:grid-cols-3">
        <Stat label="Te falta pagar" value={mxn(pending.amount)} hint={`${pending.installments} mensualidades`} />
        <Stat
          label="Termina"
          value={pending.lastMonth ? monthLabel(pending.lastMonth) : "—"}
          hint="última mensualidad pendiente"
        />
        <Stat
          label="Próximo mes"
          value={months[0] ? mxn(months[0].total) : "—"}
          hint={months[0] ? monthLabel(months[0].key) : "sin mensualidades"}
        />
      </section>

      <section className="mt-12 flex flex-col gap-4">
        {months.map((month, index) => (
          <Card key={month.key} className="p-0">
            <details open={index === 0}>
              <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-6 py-5">
                <div>
                  <p className="text-xl font-semibold capitalize tracking-[-0.03em]">{monthLabel(month.key)}</p>
                  <p className="text-sm text-muted">
                    {month.items.length} mensualidades
                    {month.finishing > 0 ? ` · ${month.finishing} termina${month.finishing > 1 ? "n" : ""}` : ""}
                  </p>
                </div>
                <p className="text-xl font-bold tracking-[-0.03em]">{mxn(month.total)}</p>
              </summary>
              <div className="divide-y divide-line border-t border-line">
                {month.items.map((item) => {
                  const card = cardOf(item.card_id);
                  return (
                    <div key={`${item.transaction_id}-${item.due_date}`} className="flex items-center justify-between gap-4 px-6 py-4">
                      <div className="min-w-0">
                        <p className="truncate font-semibold">{item.merchant}</p>
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted">
                          <span>Vence {dayLabel(item.due_date)}</span>
                          {card ? <CardChip card={card} /> : null}
                          <Tag>
                            {item.installment_number} de {item.installments}
                            {item.installment_number >= item.installments ? " · última" : ""}
                          </Tag>
                        </div>
                      </div>
                      <p className="font-semibold tracking-[-0.03em]">{mxn(item.amount)}</p>
                    </div>
                  );
                })}
              </div>
            </details>
          </Card>
        ))}
        {months.length === 0 ? (
          <Card>
            <p className="text-muted">
              No hay mensualidades pendientes. Las compras a meses que registres aparecen aquí, mes por mes.
            </p>
          </Card>
        ) : null}
      </section>
    </>
  );
}
