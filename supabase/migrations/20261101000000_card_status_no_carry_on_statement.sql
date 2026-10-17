-- El pago para no generar intereses de un estado de cuenta ya viene NETO de los pagos hechos antes del
-- corte. Si además se arrastraba el excedente del ciclo anterior, ese pago se contaba dos veces y el
-- ciclo salía "pagado" sin estarlo (RappiCard: SPEI de $7,000 del 29 sep contado contra el corte del
-- 15 oct, que ya lo había descontado). El excedente solo se arrastra cuando el objetivo es una
-- estimación de la app, que no descuenta pagos.
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
    when closed.due_date >= p_ref then 'pending'
    when exists (select 1 from public.card_payments cp where cp.card_id = p_card) then 'overdue'
    else 'untracked'
  end;

  return query select
    closed.cycle_end, closed.due_date, round(v_target, 2), coalesce(t.source, 'estimate'),
    t.minimum, round(v_paid, 2), round(greatest(0, v_target - v_paid), 2), v_status;
end $$;
