-- El saldo de meses sin intereses ("msi_remaining") ahora incluye las compras con mensualidad 0
-- (la primera mensualidad cae en el corte siguiente): antes se ignoraban y la deuda total salía corta.
-- La mensualidad del ciclo (cycle_msi_total) no cambia: esas compras aún no entran al pago de este ciclo.
create or replace function public.card_cycle_estimate(p_card_id uuid, p_ref date default current_date)
returns table (
  cycle_start date, cycle_end date, due_date date,
  cycle_contado_total numeric, cycle_msi_total numeric, cycle_total numeric,
  msi_remaining numeric, credit_limit numeric, usage_pct numeric
)
language plpgsql stable security invoker
as $$
declare
  c public.cards%rowtype;
  cy record;
begin
  select * into c from public.cards where id = p_card_id;
  if not found then
    return;
  end if;

  select * into cy from public.card_cycle(c.cutoff_day, c.due_day, p_ref);

  return query
  with contado as (
    select coalesce(sum(t.amount), 0) as total
    from public.transactions t
    where t.card_id = c.id and t.payment_mode = 'contado'
      and t.occurred_at between cy.cycle_start and cy.cycle_end
  ),
  msi as (
    select
      t.amount / t.installments as monthly,
      t.installments,
      coalesce(t.installment_number, 1) + (
        (extract(year from cy.cycle_end)::int * 12 + extract(month from cy.cycle_end)::int)
        - (extract(year from pc.cycle_end)::int * 12 + extract(month from pc.cycle_end)::int)
      ) as n_in_cycle
    from public.transactions t
    cross join lateral public.card_cycle(c.cutoff_day, c.due_day, t.occurred_at) pc
    where t.card_id = c.id and t.payment_mode = 'msi'
      and pc.cycle_end <= cy.cycle_end
  ),
  agg as (
    select
      coalesce(sum(monthly) filter (where n_in_cycle between 1 and installments), 0) as cycle_msi,
      coalesce(sum(monthly * (installments - n_in_cycle)) filter (where n_in_cycle between 0 and installments), 0) as remaining
    from msi
  )
  select
    cy.cycle_start, cy.cycle_end, cy.due_date,
    contado.total,
    round(agg.cycle_msi, 2),
    round(contado.total + agg.cycle_msi, 2),
    round(agg.remaining, 2),
    c.credit_limit,
    case when c.credit_limit > 0
         then round((contado.total + agg.cycle_msi + agg.remaining) / c.credit_limit * 100, 2)
         else 0 end
  from contado, agg;
end $$;
