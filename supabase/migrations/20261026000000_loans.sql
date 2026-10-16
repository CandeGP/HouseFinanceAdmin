-- Préstamos entre personas ("le presté 300 a mi hermana"). Van aparte de los gastos: ni el préstamo
-- ni el reembolso mueven los reportes de gasto. Mismo modelo de privacidad que el resto: el hogar lee
-- todo y solo quien lo creó escribe.

create type public.loan_direction as enum ('lent', 'borrowed');

create table public.loans (
  id           uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households(id) on delete cascade,
  owner_id     uuid not null references auth.users(id),
  person       text not null check (length(trim(person)) > 0),
  direction    public.loan_direction not null,   -- lent: yo presté · borrowed: me prestaron
  amount       numeric(12,2) not null check (amount > 0),
  occurred_on  date not null default current_date,
  method       public.payment_method not null,   -- cómo se movió el dinero
  note         text,
  forgiven_at  timestamptz,                      -- perdonado: deja de contar como deuda
  created_at   timestamptz not null default now(),
  unique (id, household_id)
);

create index loans_household_idx on public.loans (household_id, occurred_on desc);

create table public.loan_repayments (
  id           uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households(id) on delete cascade,
  owner_id     uuid not null references auth.users(id),
  loan_id      uuid not null,
  amount       numeric(12,2) not null check (amount > 0),
  paid_on      date not null default current_date,
  method       public.payment_method not null,
  note         text,
  created_at   timestamptz not null default now(),
  foreign key (loan_id, household_id) references public.loans (id, household_id) on delete cascade
);

create index loan_repayments_loan_idx on public.loan_repayments (loan_id);

alter table public.loans enable row level security;
alter table public.loan_repayments enable row level security;

create policy loans_read on public.loans for select to authenticated
  using (public.is_household_member(household_id));
create policy loans_insert on public.loans for insert to authenticated
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy loans_update on public.loans for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy loans_delete on public.loans for delete to authenticated
  using (owner_id = (select auth.uid()));

create policy lr_read on public.loan_repayments for select to authenticated
  using (public.is_household_member(household_id));
create policy lr_insert on public.loan_repayments for insert to authenticated
  with check (
    owner_id = (select auth.uid())
    and public.is_household_member(household_id)
    and exists (select 1 from public.loans l where l.id = loan_id and l.owner_id = (select auth.uid()))
  );
create policy lr_delete on public.loan_repayments for delete to authenticated
  using (owner_id = (select auth.uid()));

-- Los abonos no pueden sumar más que el préstamo.
create function public.loan_repayment_cap() returns trigger
language plpgsql security invoker
as $$
declare
  v_amount numeric;
  v_paid   numeric;
begin
  select l.amount into v_amount from public.loans l where l.id = new.loan_id;
  select coalesce(sum(r.amount), 0) into v_paid from public.loan_repayments r where r.loan_id = new.loan_id;
  if v_paid + new.amount > v_amount then
    raise exception 'repayment exceeds the loan amount' using errcode = '23514';
  end if;
  return new;
end $$;

create trigger lr_cap before insert on public.loan_repayments
  for each row execute function public.loan_repayment_cap();
