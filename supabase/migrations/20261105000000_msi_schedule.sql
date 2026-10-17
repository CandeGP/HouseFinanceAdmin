-- Calendario de compras a meses: cada mensualidad que falta, ciclo por ciclo, con la fecha en que se paga.
-- Usa las mismas reglas que card_cycle_estimate (misma cuenta de "en qué mensualidad va" y mismo ciclo), así
-- que lo que se ve en un mes futuro es exactamente lo que ese ciclo sumará cuando llegue. Sirve igual para
-- compras que entraron por estado de cuenta que para las capturadas a mano.
-- p_ref: día de referencia (el ciclo abierto de cada tarjeta es el primero). p_months: cuántos ciclos hacia adelante.
-- security invoker: respeta RLS (el hogar ve todas las tarjetas).
create or replace function public.msi_schedule(p_ref date default current_date, p_months int default 12)
returns table (
  card_id uuid, transaction_id uuid, merchant text,
  cycle_end date, due_date date,
  installment_number int, installments int, amount numeric
)
language sql stable security invoker
as $$
  select
    c.id,
    t.id,
    t.merchant,
    target.cycle_end,
    public.card_due_date(c.id, target.cycle_end, due.due_date),
    n.value,
    t.installments,
    round(t.amount / t.installments, 2)
  from public.cards c
  cross join lateral public.card_cycle(c.cutoff_day, c.due_day, p_ref) cur
  cross join lateral generate_series(0, greatest(p_months, 0)) as k(i)
  cross join lateral (select public.month_day(cur.cycle_end, k.i, c.cutoff_day) as cycle_end) target
  cross join lateral public.card_cycle(c.cutoff_day, c.due_day, target.cycle_end) due
  join public.transactions t on t.card_id = c.id and t.payment_mode = 'msi' and t.installments > 0
  cross join lateral public.card_cycle(c.cutoff_day, c.due_day, t.occurred_at) pc
  cross join lateral (
    select coalesce(t.installment_number, 1) + (
      (extract(year from target.cycle_end)::int * 12 + extract(month from target.cycle_end)::int)
      - (extract(year from pc.cycle_end)::int * 12 + extract(month from pc.cycle_end)::int)
    ) as value
  ) n
  where not c.archived
    and pc.cycle_end <= target.cycle_end
    and n.value between 1 and t.installments
  order by 5, c.name, t.merchant;
$$;

revoke all on function public.msi_schedule(date, int) from public, anon;
grant execute on function public.msi_schedule(date, int) to authenticated;
