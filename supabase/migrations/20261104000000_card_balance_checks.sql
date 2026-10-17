-- Conciliación contra el banco: cada vez que se captura el "saldo utilizado" que muestra el banco, queda el
-- registro (monto y fecha) para comparar contra lo que suma la app y saber cuándo fue la última revisión.
-- Mismo modelo de privacidad que los pagos: el hogar lee todo, solo la dueña de la tarjeta escribe.

create table public.card_balance_checks (
  id           uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households(id) on delete cascade,
  owner_id     uuid not null references auth.users(id),
  card_id      uuid not null,
  bank_balance numeric(12,2) not null check (bank_balance >= 0),   -- saldo utilizado según el banco
  checked_on   date not null default current_date,
  created_at   timestamptz not null default now(),
  foreign key (card_id, household_id) references public.cards (id, household_id) on delete cascade
);

create index card_balance_checks_card_idx on public.card_balance_checks (card_id, checked_on desc, created_at desc);

alter table public.card_balance_checks enable row level security;

create policy cbc_read on public.card_balance_checks for select to authenticated
  using (public.is_household_member(household_id));
create policy cbc_insert on public.card_balance_checks for insert to authenticated
  with check (
    owner_id = (select auth.uid())
    and public.is_household_member(household_id)
    and exists (select 1 from public.cards c where c.id = card_id and c.owner_id = (select auth.uid()))
  );
create policy cbc_delete on public.card_balance_checks for delete to authenticated
  using (owner_id = (select auth.uid()));
