-- Finanzas de pareja: esquema base (hogar, miembros, tarjetas, gastos, ciclos).
-- Solo MXN. Los datos son privados por hogar vía RLS; dentro del hogar:
--   * cada miembro administra (INSERT/UPDATE/DELETE) únicamente sus propios registros
--   * ambos miembros pueden LEER todo el hogar (vista compartida, solo lectura)

-- ---------------------------------------------------------------- tipos
create type public.member_role   as enum ('admin', 'member');
create type public.payment_mode  as enum ('contado', 'msi');
create type public.tx_source     as enum ('manual', 'notification', 'email', 'statement');
create type public.tx_status     as enum ('pending_review', 'confirmed');

-- ---------------------------------------------------------------- tablas
create table public.households (
  id          uuid primary key default gen_random_uuid(),
  name        text not null check (length(trim(name)) > 0),
  max_members int  not null default 2 check (max_members >= 1),
  created_at  timestamptz not null default now()
);

create table public.members (
  household_id uuid not null references public.households(id) on delete cascade,
  user_id      uuid not null references auth.users(id) on delete cascade,
  role         public.member_role not null default 'member',
  display_name text not null check (length(trim(display_name)) > 0),
  created_at   timestamptz not null default now(),
  primary key (household_id, user_id)
);

create table public.household_invites (
  code         text primary key,
  household_id uuid not null references public.households(id) on delete cascade,
  created_by   uuid not null references auth.users(id),
  expires_at   timestamptz not null default now() + interval '7 days',
  used_at      timestamptz,
  used_by      uuid references auth.users(id)
);

create table public.categories (
  id           uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households(id) on delete cascade,
  name         text not null check (length(trim(name)) > 0),
  icon         text,
  created_at   timestamptz not null default now(),
  unique (household_id, name),
  unique (id, household_id)
);

create table public.cards (
  id           uuid primary key default gen_random_uuid(),
  household_id uuid not null references public.households(id) on delete cascade,
  owner_id     uuid not null references auth.users(id),
  name         text not null check (length(trim(name)) > 0),
  bank         text not null,
  last4        text not null check (last4 ~ '^[0-9]{4}$'),
  credit_limit numeric(12,2) not null check (credit_limit >= 0),
  cutoff_day   int not null check (cutoff_day between 1 and 31),
  due_day      int not null check (due_day between 1 and 31),
  color        text,
  archived     boolean not null default false,
  created_at   timestamptz not null default now(),
  unique (id, household_id)
);
create index cards_household_idx on public.cards (household_id);

create table public.transactions (
  id                 uuid primary key default gen_random_uuid(),
  household_id       uuid not null references public.households(id) on delete cascade,
  owner_id           uuid not null references auth.users(id),
  card_id            uuid,
  category_id        uuid,
  merchant           text not null check (length(trim(merchant)) > 0),
  amount             numeric(12,2) not null,
  occurred_at        date not null,
  payment_mode       public.payment_mode not null default 'contado',
  installments       int,
  installment_number int,
  notes              text,
  source             public.tx_source not null default 'manual',
  status             public.tx_status not null default 'confirmed',
  dedup_hash         text,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  constraint tx_amount_positive check (amount > 0),
  constraint tx_payment_mode_shape check (
    (payment_mode = 'contado' and installments is null and installment_number is null)
    or
    (payment_mode = 'msi' and installments is not null and installments >= 2
       and (installment_number is null or installment_number between 1 and installments))
  ),
  foreign key (card_id, household_id)     references public.cards (id, household_id),
  foreign key (category_id, household_id) references public.categories (id, household_id)
);
create index tx_household_date_idx on public.transactions (household_id, occurred_at desc);
create index tx_card_date_idx      on public.transactions (card_id, occurred_at);
create unique index tx_dedup_idx   on public.transactions (household_id, dedup_hash)
  where dedup_hash is not null;

create function public.set_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end $$;

create trigger tx_set_updated_at before update on public.transactions
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------- helpers
create function public.is_household_member(p_household uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.members m
    where m.household_id = p_household and m.user_id = (select auth.uid())
  );
$$;

-- ---------------------------------------------------------------- RLS
alter table public.households        enable row level security;
alter table public.members           enable row level security;
alter table public.household_invites enable row level security;
alter table public.categories        enable row level security;
alter table public.cards             enable row level security;
alter table public.transactions      enable row level security;

create policy households_read on public.households for select to authenticated
  using (public.is_household_member(id));

create policy members_read on public.members for select to authenticated
  using (public.is_household_member(household_id));

-- household_invites: sin políticas -> solo accesible vía RPC (security definer)

create policy categories_read on public.categories for select to authenticated
  using (public.is_household_member(household_id));
create policy categories_write on public.categories for all to authenticated
  using (public.is_household_member(household_id))
  with check (public.is_household_member(household_id));

create policy cards_read on public.cards for select to authenticated
  using (public.is_household_member(household_id));
create policy cards_insert on public.cards for insert to authenticated
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy cards_update on public.cards for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy cards_delete on public.cards for delete to authenticated
  using (owner_id = (select auth.uid()));

create policy tx_read on public.transactions for select to authenticated
  using (public.is_household_member(household_id));
create policy tx_insert on public.transactions for insert to authenticated
  with check (
    owner_id = (select auth.uid())
    and public.is_household_member(household_id)
    and (card_id is null or exists (
      select 1 from public.cards c where c.id = card_id and c.owner_id = (select auth.uid())))
  );
create policy tx_update on public.transactions for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (
    owner_id = (select auth.uid())
    and public.is_household_member(household_id)
    and (card_id is null or exists (
      select 1 from public.cards c where c.id = card_id and c.owner_id = (select auth.uid())))
  );
create policy tx_delete on public.transactions for delete to authenticated
  using (owner_id = (select auth.uid()));

-- ---------------------------------------------------------------- RPC: hogar e invitaciones
create function public.create_household(p_name text, p_display_name text) returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_hid uuid;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;

  insert into public.households (name) values (p_name) returning id into v_hid;
  insert into public.members (household_id, user_id, role, display_name)
    values (v_hid, v_uid, 'admin', p_display_name);

  insert into public.categories (household_id, name, icon)
  select v_hid, c.name, c.icon
  from (values
    ('Gasolina', 'fuel'), ('Comida y restaurantes', 'utensils'), ('Supermercado', 'cart'),
    ('Juegos y entretenimiento', 'gamepad'), ('Ropa', 'shirt'), ('Compras en línea', 'package'),
    ('Servicios y suscripciones', 'repeat'), ('Salud', 'heart'), ('Transporte', 'car'),
    ('Hogar', 'home'), ('Educación', 'book'), ('Otros', 'tag')
  ) as c(name, icon);

  return v_hid;
end $$;

create function public.create_invite(p_household uuid) returns text
language plpgsql security definer set search_path = ''
as $$
declare
  v_uid  uuid := (select auth.uid());
  v_code text := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10));
begin
  if not exists (
    select 1 from public.members
    where household_id = p_household and user_id = v_uid and role = 'admin'
  ) then
    raise exception 'only an admin can invite' using errcode = '42501';
  end if;

  insert into public.household_invites (code, household_id, created_by)
    values (v_code, p_household, v_uid);
  return v_code;
end $$;

create function public.accept_invite(p_code text, p_display_name text) returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_inv public.household_invites%rowtype;
  v_max int;
  v_count int;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;

  select * into v_inv from public.household_invites where code = upper(p_code) for update;
  if not found or v_inv.used_at is not null or v_inv.expires_at < now() then
    raise exception 'invalid or expired invite';
  end if;

  select max_members into v_max from public.households where id = v_inv.household_id;
  select count(*) into v_count from public.members where household_id = v_inv.household_id;
  if v_count >= v_max then
    raise exception 'household is full';
  end if;

  insert into public.members (household_id, user_id, role, display_name)
    values (v_inv.household_id, v_uid, 'member', p_display_name);
  update public.household_invites set used_at = now(), used_by = v_uid where code = v_inv.code;

  return v_inv.household_id;
end $$;

revoke all on function public.create_household(text, text) from public, anon;
revoke all on function public.create_invite(uuid)          from public, anon;
revoke all on function public.accept_invite(text, text)    from public, anon;
grant execute on function public.create_household(text, text) to authenticated;
grant execute on function public.create_invite(uuid)          to authenticated;
grant execute on function public.accept_invite(text, text)    to authenticated;

-- ---------------------------------------------------------------- ciclos de tarjeta
-- Día `p_day` del mes (base + offset) recortado al último día de ese mes.
create function public.month_day(p_base date, p_offset int, p_day int) returns date
language sql immutable
as $$
  select least(
    (date_trunc('month', p_base) + make_interval(months => p_offset))::date + (p_day - 1),
    (date_trunc('month', p_base) + make_interval(months => p_offset + 1))::date - 1
  );
$$;

-- Ciclo de facturación que contiene `p_ref`. El día de corte pertenece al ciclo que cierra.
create function public.card_cycle(p_cutoff_day int, p_due_day int, p_ref date)
returns table (cycle_start date, cycle_end date, due_date date)
language plpgsql immutable
as $$
declare
  v_end  date;
  v_due  date;
begin
  if p_cutoff_day not between 1 and 31 or p_due_day not between 1 and 31 then
    raise exception 'cutoff_day and due_day must be within 1..31' using errcode = '22023';
  end if;

  v_end := public.month_day(p_ref, 0, p_cutoff_day);
  if p_ref > v_end then
    v_end := public.month_day(p_ref, 1, p_cutoff_day);
  end if;

  v_due := public.month_day(v_end, 0, p_due_day);
  if v_due <= v_end then
    v_due := public.month_day(v_end, 1, p_due_day);
  end if;

  return query select public.month_day(v_end, -1, p_cutoff_day) + 1, v_end, v_due;
end $$;

-- Estimación del ciclo de una tarjeta: contado + mensualidades MSI que caen en el ciclo,
-- y % de uso = (total del ciclo + saldo MSI pendiente) / límite.
-- security invoker: respeta RLS (ambos miembros pueden consultar tarjetas del hogar).
create function public.card_cycle_estimate(p_card_id uuid, p_ref date default current_date)
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
  msi_active as (
    select * from msi where n_in_cycle between 1 and installments
  ),
  agg as (
    select
      coalesce(sum(monthly), 0)                              as cycle_msi,
      coalesce(sum(monthly * (installments - n_in_cycle)), 0) as remaining
    from msi_active
  )
  select
    cy.cycle_start, cy.cycle_end, cy.due_date,
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
