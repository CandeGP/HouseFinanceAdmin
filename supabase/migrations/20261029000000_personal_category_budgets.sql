-- Presupuesto por categoría INDIVIDUAL: cada persona fija el suyo y ve solo su propio gasto.
-- Sustituye al presupuesto del hogar de 20261027 (la columna categories.monthly_budget, que nunca se usó
-- en producción, se elimina al final).

create table public.category_budgets (
  id             uuid primary key default gen_random_uuid(),
  household_id   uuid not null references public.households(id) on delete cascade,
  owner_id       uuid not null references auth.users(id),
  category_id    uuid not null,
  monthly_budget numeric(12,2) not null check (monthly_budget > 0),
  created_at     timestamptz not null default now(),
  unique (owner_id, category_id),
  foreign key (category_id, household_id) references public.categories (id, household_id) on delete cascade
);

alter table public.category_budgets enable row level security;

-- Privado: cada quien ve y cambia solo los suyos (la pareja no ve el presupuesto del otro).
create policy cb_read on public.category_budgets for select to authenticated
  using (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy cb_insert on public.category_budgets for insert to authenticated
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy cb_update on public.category_budgets for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy cb_delete on public.category_budgets for delete to authenticated
  using (owner_id = (select auth.uid()));

-- Gasto del mes calendario de [p_ref] por categoría, SOLO de quien consulta (sus gastos y sus pagos en
-- efectivo) frente a SU presupuesto. Las compras a meses cuentan por su mensualidad. No cuenta cobros
-- pendientes, pagos a tarjeta ni préstamos. Devuelve todas las categorías del hogar, con o sin presupuesto.
create or replace function public.category_month_spend(p_ref date default current_date)
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
     where t.owner_id = (select auth.uid())
       and t.status = 'confirmed' and t.category_id is not null
       and t.occurred_at between b.s and b.e
     group by t.category_id
  ),
  mp as (
    select m.category_id, sum(m.amount) as spent
      from public.manual_payments m, bounds b
     where m.owner_id = (select auth.uid())
       and m.category_id is not null
       and (m.paid_at at time zone 'America/Mexico_City')::date between b.s and b.e
     group by m.category_id
  ),
  bud as (
    select cb.category_id, cb.monthly_budget
      from public.category_budgets cb
     where cb.owner_id = (select auth.uid())
  )
  select c.id, b.s, b.e,
         round(coalesce(tx.spent, 0) + coalesce(mp.spent, 0), 2),
         bud.monthly_budget,
         case when bud.monthly_budget is null then null
              else round((coalesce(tx.spent, 0) + coalesce(mp.spent, 0)) / bud.monthly_budget * 100, 1) end
    from public.categories c
   cross join bounds b
    left join tx on tx.category_id = c.id
    left join mp on mp.category_id = c.id
    left join bud on bud.category_id = c.id
$$;

-- Fija (o quita, con null) MI presupuesto de una categoría. security invoker: RLS decide; una categoría
-- de otro hogar no se ve y falla.
create function public.set_category_budget(p_category uuid, p_amount numeric)
returns void
language plpgsql security invoker
as $$
declare
  v_hid uuid;
begin
  if p_amount is null then
    delete from public.category_budgets
     where owner_id = (select auth.uid()) and category_id = p_category;
    return;
  end if;

  select c.household_id into v_hid from public.categories c where c.id = p_category;
  if not found then
    raise exception 'category not found' using errcode = 'P0002';
  end if;

  insert into public.category_budgets (household_id, owner_id, category_id, monthly_budget)
  values (v_hid, (select auth.uid()), p_category, p_amount)
  on conflict (owner_id, category_id) do update set monthly_budget = excluded.monthly_budget;
end $$;

revoke all on function public.set_category_budget(uuid, numeric) from public, anon;
grant execute on function public.set_category_budget(uuid, numeric) to authenticated;

-- El presupuesto ya no es de la categoría (del hogar), sino de cada persona.
alter table public.categories drop column monthly_budget;
