-- La fecha límite de pago sale del estado de cuenta, no solo de "día de pago":
--   · si hay estado de ese corte, manda su fecha límite;
--   · si no, se copia el desfase en meses (0 = el mismo mes, 1 = el mes siguiente) del último estado importado;
--   · y si no hay ningún estado, se usa el cálculo de siempre (card_cycle).
-- Caso real: Plata corta el 18 y paga el 19 del mes siguiente; sin esto la app lo veía vencer el día 19 del
-- mismo mes, es decir, un día después del corte.
create or replace function public.card_due_date(p_card uuid, p_cycle_end date, p_default date)
returns date
language sql stable security invoker
as $$
  select coalesce(
    (select s.due_date
       from public.statements s
      where s.card_id = p_card and s.cutoff_date = p_cycle_end and s.due_date is not null
      order by s.imported_at desc
      limit 1),
    (select public.month_day(p_cycle_end, last_stmt.months_later, c.due_day)
       from public.cards c
       cross join lateral (
         select (extract(year from s.due_date)::int * 12 + extract(month from s.due_date)::int)
              - (extract(year from s.cutoff_date)::int * 12 + extract(month from s.cutoff_date)::int) as months_later
           from public.statements s
          where s.card_id = p_card and s.due_date is not null
          order by s.cutoff_date desc
          limit 1
       ) last_stmt
      where c.id = p_card),
    p_default
  );
$$;

revoke all on function public.card_due_date(uuid, date, date) from public, anon;
grant execute on function public.card_due_date(uuid, date, date) to authenticated;

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
    cy.cycle_start, cy.cycle_end, public.card_due_date(c.id, cy.cycle_end, cy.due_date),
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

create or replace function public.card_cycle_status(p_card uuid, p_ref date default current_date)
returns table (
  cutoff_date date, due_date date, target_amount numeric, target_source text,
  minimum_payment numeric, paid_amount numeric, remaining numeric, status text
)
language plpgsql stable security invoker
as $$
declare
  c public.cards%rowtype;
  cur record;
  closed record;
  before_closed record;
  t record;
  t_before record;
  v_target numeric;
  v_paid numeric;
  v_carry numeric;
  v_due date;
  v_status text;
begin
  select * into c from public.cards where id = p_card;
  if not found then
    return;
  end if;

  select * into cur from public.card_cycle(c.cutoff_day, c.due_day, p_ref);
  select * into closed from public.card_cycle(c.cutoff_day, c.due_day, cur.cycle_start - 1);
  select * into before_closed from public.card_cycle(c.cutoff_day, c.due_day, closed.cycle_start - 1);

  select * into t from public.card_cycle_target(p_card, closed.cycle_end);
  select * into t_before from public.card_cycle_target(p_card, before_closed.cycle_end);

  v_due := public.card_due_date(p_card, closed.cycle_end, closed.due_date);
  v_target := coalesce(t.target, 0);
  v_carry := case when t.source = 'statement' then 0 else greatest(0,
    (select coalesce(sum(cp.amount), 0) from public.card_payments cp
      where cp.card_id = p_card and cp.cutoff_date = before_closed.cycle_end)
    - coalesce(t_before.target, 0)) end;
  v_paid := v_carry + (select coalesce(sum(cp.amount), 0) from public.card_payments cp
                        where cp.card_id = p_card and cp.cutoff_date = closed.cycle_end);

  v_status := case
    when v_target <= 0 or v_paid >= v_target - 0.5 then 'paid'
    when t.minimum is not null and t.minimum > 0 and v_paid >= t.minimum then 'minimum_only'
    when v_paid > 0 then 'partial'
    when v_due >= p_ref then 'pending'
    when exists (select 1 from public.card_payments cp where cp.card_id = p_card) then 'overdue'
    else 'untracked'
  end;

  return query select
    closed.cycle_end, v_due, round(v_target, 2), coalesce(t.source, 'estimate'),
    t.minimum, round(v_paid, 2), round(greatest(0, v_target - v_paid), 2), v_status;
end $$;
