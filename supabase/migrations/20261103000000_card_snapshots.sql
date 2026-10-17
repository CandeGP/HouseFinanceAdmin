-- Estimación del ciclo y estado de pago de TODAS las tarjetas del hogar en una sola llamada.
-- El Resumen hacía una consulta por tarjeta (y otra más por su estado de pago): con 12 tarjetas, 24 viajes.
-- security invoker: respeta RLS igual que las funciones que envuelve.
create or replace function public.card_snapshots(p_ref date default current_date)
returns table (
  card_id uuid,
  cycle_start date, cycle_end date, due_date date,
  cycle_contado_total numeric, cycle_msi_total numeric, cycle_total numeric,
  msi_remaining numeric, credit_limit numeric, usage_pct numeric,
  st_cutoff_date date, st_due_date date, st_target_amount numeric, st_target_source text,
  st_minimum_payment numeric, st_paid_amount numeric, st_remaining numeric, st_status text
)
language sql stable security invoker
as $$
  select c.id,
         e.cycle_start, e.cycle_end, e.due_date,
         e.cycle_contado_total, e.cycle_msi_total, e.cycle_total,
         e.msi_remaining, e.credit_limit, e.usage_pct,
         s.cutoff_date, s.due_date, s.target_amount, s.target_source,
         s.minimum_payment, s.paid_amount, s.remaining, s.status
  from public.cards c
  left join lateral public.card_cycle_estimate(c.id, p_ref) e on true
  left join lateral public.card_cycle_status(c.id, p_ref) s on true
  where not c.archived;
$$;

revoke all on function public.card_snapshots(date) from public, anon;
grant execute on function public.card_snapshots(date) to authenticated;
