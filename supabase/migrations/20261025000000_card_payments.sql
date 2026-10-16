-- Pagos a tarjeta: cuánto se ha pagado de cada ciclo cerrado y si queda pendiente.
-- Mismo modelo de privacidad que el resto: el hogar lee todo, solo la dueña de la tarjeta escribe.

create table public.card_payments (
  id           uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households(id) on delete cascade,
  owner_id     uuid not null references auth.users(id),
  card_id      uuid not null,
  cutoff_date  date not null,                 -- ciclo que se paga (su fecha de corte)
  amount       numeric(12,2) not null check (amount > 0),
  paid_at      timestamptz not null default now(),
  method       public.payment_method not null,
  notes        text,
  created_at   timestamptz not null default now(),
  foreign key (card_id, household_id) references public.cards (id, household_id)
);

create index card_payments_card_cutoff_idx on public.card_payments (card_id, cutoff_date);

alter table public.card_payments enable row level security;

create policy cp_read on public.card_payments for select to authenticated
  using (public.is_household_member(household_id));
create policy cp_insert on public.card_payments for insert to authenticated
  with check (
    owner_id = (select auth.uid())
    and public.is_household_member(household_id)
    and exists (select 1 from public.cards c where c.id = card_id and c.owner_id = (select auth.uid()))
  );
-- Sin política de update: un pago mal capturado se borra y se registra de nuevo.
create policy cp_delete on public.card_payments for delete to authenticated
  using (owner_id = (select auth.uid()));

-- Monto que se debe pagar de un ciclo: el "pago para no generar intereses" del estado de cuenta
-- si se importó; si no, la estimación de la app (contado + mensualidades MSI del ciclo).
create function public.card_cycle_target(p_card uuid, p_cutoff date)
returns table (target numeric, source text, minimum numeric)
language plpgsql stable security invoker
as $$
declare
  s public.statements%rowtype;
begin
  select st.* into s
  from public.statements st
  where st.card_id = p_card and st.cutoff_date = p_cutoff
  order by st.imported_at desc
  limit 1;

  if found and s.pay_no_interest is not null then
    return query select s.pay_no_interest, 'statement'::text, s.minimum_payment;
    return;
  end if;

  return query select e.cycle_total, 'estimate'::text, null::numeric
  from public.card_cycle_estimate(p_card, p_cutoff) e;
end $$;

-- Estado del último ciclo cerrado de la tarjeta (el que toca pagar).
--   paid          cubierto (o no había nada que pagar)
--   minimum_only  se pagó al menos el mínimo pero no el total (habrá intereses)
--   partial       se pagó algo, menos del mínimo
--   pending       aún sin pagos y dentro del plazo
--   overdue       pasó el límite de pago sin cubrir (solo si la tarjeta ya lleva pagos registrados)
--   untracked     pasó el límite y la tarjeta nunca ha registrado pagos: no se sabe, no se acusa
-- Si el ciclo anterior se pagó de más, el excedente se suma a este (un solo nivel hacia atrás).
create function public.card_cycle_status(p_card uuid, p_ref date default current_date)
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
  v_carry := greatest(0,
    (select coalesce(sum(cp.amount), 0) from public.card_payments cp
      where cp.card_id = p_card and cp.cutoff_date = before_closed.cycle_end)
    - coalesce(t_before.target, 0));
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

revoke all on function public.card_cycle_target(uuid, date) from public, anon;
revoke all on function public.card_cycle_status(uuid, date) from public, anon;
grant execute on function public.card_cycle_target(uuid, date) to authenticated;
grant execute on function public.card_cycle_status(uuid, date) to authenticated;
