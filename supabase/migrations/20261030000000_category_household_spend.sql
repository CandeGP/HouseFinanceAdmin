-- Las categorías son del hogar y las ven los dos; solo el presupuesto es de cada persona.
-- category_month_spend ahora devuelve, además del gasto de quien consulta (contra SU presupuesto),
-- lo que gastó el hogar completo en la categoría, como contexto.
-- Cambia el tipo de resultado, así que hay que borrar y volver a crear la función.

drop function public.category_month_spend(date);

create function public.category_month_spend(p_ref date default current_date)
returns table (
  category_id uuid, month_start date, month_end date,
  spent numeric,            -- solo de quien consulta
  household_spent numeric,  -- de los dos (visible para ambos, igual que Gastos con "Ambos")
  budget numeric,           -- el presupuesto de quien consulta
  pct numeric               -- spent contra budget
)
language sql stable security invoker
as $$
  with bounds as (
    select date_trunc('month', p_ref)::date as s,
           (date_trunc('month', p_ref) + interval '1 month' - interval '1 day')::date as e
  ),
  tx as (
    select t.category_id,
           sum(case when t.payment_mode = 'msi' and coalesce(t.installments, 0) > 0
                    then t.amount / t.installments else t.amount end) as total,
           sum(case when t.owner_id = (select auth.uid())
                    then (case when t.payment_mode = 'msi' and coalesce(t.installments, 0) > 0
                               then t.amount / t.installments else t.amount end)
                    else 0 end) as mine
      from public.transactions t, bounds b
     where t.status = 'confirmed' and t.category_id is not null
       and t.occurred_at between b.s and b.e
     group by t.category_id
  ),
  mp as (
    select m.category_id,
           sum(m.amount) as total,
           sum(case when m.owner_id = (select auth.uid()) then m.amount else 0 end) as mine
      from public.manual_payments m, bounds b
     where m.category_id is not null
       and (m.paid_at at time zone 'America/Mexico_City')::date between b.s and b.e
     group by m.category_id
  ),
  bud as (
    select cb.category_id, cb.monthly_budget
      from public.category_budgets cb
     where cb.owner_id = (select auth.uid())
  )
  select c.id, b.s, b.e,
         round(coalesce(tx.mine, 0) + coalesce(mp.mine, 0), 2),
         round(coalesce(tx.total, 0) + coalesce(mp.total, 0), 2),
         bud.monthly_budget,
         case when bud.monthly_budget is null then null
              else round((coalesce(tx.mine, 0) + coalesce(mp.mine, 0)) / bud.monthly_budget * 100, 1) end
    from public.categories c
   cross join bounds b
    left join tx on tx.category_id = c.id
    left join mp on mp.category_id = c.id
    left join bud on bud.category_id = c.id
$$;

revoke all on function public.category_month_spend(date) from public, anon;
grant execute on function public.category_month_spend(date) to authenticated;
