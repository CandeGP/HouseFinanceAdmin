-- Pagos en efectivo / transferencia con evidencia.
-- Mismo modelo de privacidad que `transactions`: el hogar lee todo, cada quien escribe lo suyo.

create type public.payment_method as enum ('cash', 'transfer');

create table public.manual_payments (
  id            uuid primary key default gen_random_uuid(),
  household_id  uuid not null references public.households(id) on delete cascade,
  owner_id      uuid not null references auth.users(id),
  method        public.payment_method not null,
  amount        numeric(12,2) not null,
  paid_at       timestamptz not null,
  concept       text not null,
  counterparty  text,
  reference     text,
  category_id   uuid,
  notes         text,
  evidence_path text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  constraint mp_amount_positive check (amount > 0),
  constraint mp_concept_not_blank check (length(trim(concept)) > 0),
  -- la evidencia solo puede apuntar a la carpeta <hogar>/<dueño>/ del propio registro
  constraint mp_evidence_prefix check (
    evidence_path is null
    or evidence_path like household_id::text || '/' || owner_id::text || '/%'
  ),
  foreign key (category_id, household_id) references public.categories (id, household_id)
);

create index mp_household_paid_idx on public.manual_payments (household_id, paid_at desc);

create trigger mp_set_updated_at before update on public.manual_payments
  for each row execute function public.set_updated_at();

alter table public.manual_payments enable row level security;

create policy mp_read on public.manual_payments for select to authenticated
  using (public.is_household_member(household_id));
create policy mp_insert on public.manual_payments for insert to authenticated
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy mp_update on public.manual_payments for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy mp_delete on public.manual_payments for delete to authenticated
  using (owner_id = (select auth.uid()));

-- ---------------------------------------------------------------- evidencias (Storage)
-- Ruta de objeto: <household_id>/<owner_id>/<archivo>
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'evidence', 'evidence', false, 10485760,
  array['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
)
on conflict (id) do nothing;

-- Se compara como texto para que un nombre de objeto mal formado no provoque errores de cast.
create policy evidence_read on storage.objects for select to authenticated
  using (
    bucket_id = 'evidence'
    and exists (
      select 1 from public.members m
      where m.household_id::text = (storage.foldername(name))[1]
        and m.user_id = (select auth.uid())
    )
  );

create policy evidence_insert on storage.objects for insert to authenticated
  with check (
    bucket_id = 'evidence'
    and (storage.foldername(name))[2] = (select auth.uid())::text
    and exists (
      select 1 from public.members m
      where m.household_id::text = (storage.foldername(name))[1]
        and m.user_id = (select auth.uid())
    )
  );

create policy evidence_update on storage.objects for update to authenticated
  using (bucket_id = 'evidence' and (storage.foldername(name))[2] = (select auth.uid())::text)
  with check (bucket_id = 'evidence' and (storage.foldername(name))[2] = (select auth.uid())::text);

create policy evidence_delete on storage.objects for delete to authenticated
  using (bucket_id = 'evidence' and (storage.foldername(name))[2] = (select auth.uid())::text);
