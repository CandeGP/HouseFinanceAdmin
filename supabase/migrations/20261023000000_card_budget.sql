-- Presupuesto mensual por tarjeta. El gasto del mes lo calcula el servidor para que app y web
-- muestren siempre lo mismo (igual que el ciclo de la tarjeta).

alter table public.cards
  add column monthly_budget numeric(12,2)
    check (monthly_budget is null or monthly_budget > 0);

-- Gasto del mes calendario de [p_ref] en la tarjeta: compras confirmadas; las compras a meses cuentan
-- por su mensualidad (monto / meses) para no reventar el presupuesto con una compra grande.
-- security invoker: RLS decide qué tarjetas y gastos se ven.
create function public.card_month_spend(p_card uuid, p_ref date default current_date)
returns table (month_start date, month_end date, spent numeric, budget numeric, pct numeric)
language plpgsql stable security invoker
as $$
declare
  c       public.cards%rowtype;
  v_start date := date_trunc('month', p_ref)::date;
  v_end   date := (date_trunc('month', p_ref) + interval '1 month' - interval '1 day')::date;
  v_spent numeric;
begin
  select * into c from public.cards where id = p_card;
  if not found then
    return;
  end if;

  select coalesce(sum(
           case when t.payment_mode = 'msi' and coalesce(t.installments, 0) > 0
                then t.amount / t.installments
                else t.amount end), 0)
    into v_spent
    from public.transactions t
   where t.card_id = p_card
     and t.status = 'confirmed'
     and t.occurred_at between v_start and v_end;

  return query
    select v_start, v_end, round(v_spent, 2), c.monthly_budget,
           case when c.monthly_budget is null then null
                else round(v_spent / c.monthly_budget * 100, 1) end;
end $$;
