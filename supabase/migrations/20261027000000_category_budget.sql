-- Presupuesto mensual por categoría. Es del hogar: cualquiera de los dos miembros lo puede fijar
-- (las políticas de `categories` ya permiten escribir a los miembros).

alter table public.categories
  add column monthly_budget numeric(12,2)
    check (monthly_budget is null or monthly_budget > 0);

-- Gasto del mes calendario de [p_ref] por categoría. Cuenta:
--   · gastos confirmados (las compras a meses, por su mensualidad: monto / meses)
--   · pagos en efectivo o transferencia con esa categoría (fecha en hora de Ciudad de México)
-- No cuenta cobros pendientes de revisar, pagos a tarjeta ni préstamos (no son gasto).
-- Devuelve todas las categorías visibles, con o sin presupuesto.
-- security invoker: RLS decide qué categorías y gastos se ven.
create function public.category_month_spend(p_ref date default current_date)
returns table (category_id uuid, month_start date, month_end date, spent numeric, budget numeric, pct numeric)
language sql stable security invoker
as $$
  with bounds as (
    select date_trunc('month', p_ref)::date as s,
           (date_trunc('month', p_ref) + interval '1 month' - interval '1 day')::date as e
  ),
  tx as (
    select t.category_id,
           sum(case when t.payment_mode = 'msi' and coalesce(t.installments, 0) > 0
                    then t.amount / t.installments else t.amount end) as spent
      from public.transactions t, bounds b
     where t.status = 'confirmed' and t.category_id is not null
       and t.occurred_at between b.s and b.e
     group by t.category_id
  ),
  mp as (
    select m.category_id, sum(m.amount) as spent
      from public.manual_payments m, bounds b
     where m.category_id is not null
       and (m.paid_at at time zone 'America/Mexico_City')::date between b.s and b.e
     group by m.category_id
  )
  select c.id, b.s, b.e,
         round(coalesce(tx.spent, 0) + coalesce(mp.spent, 0), 2),
         c.monthly_budget,
         case when c.monthly_budget is null then null
              else round((coalesce(tx.spent, 0) + coalesce(mp.spent, 0)) / c.monthly_budget * 100, 1) end
    from public.categories c
   cross join bounds b
    left join tx on tx.category_id = c.id
    left join mp on mp.category_id = c.id
$$;

revoke all on function public.category_month_spend(date) from public, anon;
grant execute on function public.category_month_spend(date) to authenticated;
