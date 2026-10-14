-- Estados de cuenta: registro del documento, ajuste de la tarjeta con los datos del banco e
-- importación idempotente de movimientos y compras a meses (MSI).

-- La primera mensualidad puede caer en el corte SIGUIENTE a la compra (p. ej. Plata): se modela
-- con installment_number = 0 ("aún no empieza"); la estimación de ciclo ya ignora n < 1.
alter table public.transactions drop constraint tx_payment_mode_shape;
alter table public.transactions add constraint tx_payment_mode_shape check (
  (payment_mode = 'contado' and installments is null and installment_number is null)
  or
  (payment_mode = 'msi' and installments is not null and installments >= 2
     and (installment_number is null or installment_number between 0 and installments))
);

create table public.statements (
  id              uuid primary key default gen_random_uuid(),
  household_id    uuid not null references public.households(id) on delete cascade,
  owner_id        uuid not null references auth.users(id),
  card_id         uuid not null,
  bank            text,
  card_last4      text,
  period_start    date,
  period_end      date,
  cutoff_date     date not null,
  due_date        date,
  credit_limit    numeric(12,2),
  pay_no_interest numeric(12,2),
  minimum_payment numeric(12,2),
  file_path       text,
  imported_at     timestamptz not null default now(),
  unique (owner_id, card_id, cutoff_date),
  foreign key (card_id, household_id) references public.cards (id, household_id),
  constraint st_file_prefix check (
    file_path is null
    or file_path like household_id::text || '/' || owner_id::text || '/%'
  )
);

create index statements_household_cutoff_idx on public.statements (household_id, cutoff_date desc);

alter table public.statements enable row level security;

create policy st_read on public.statements for select to authenticated
  using (public.is_household_member(household_id));
create policy st_insert on public.statements for insert to authenticated
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy st_update on public.statements for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()) and public.is_household_member(household_id));
create policy st_delete on public.statements for delete to authenticated
  using (owner_id = (select auth.uid()));

-- p_statement: {bank, card_last4, period_start, period_end, cutoff_date, due_date, credit_limit,
--               pay_no_interest, minimum_payment, file_path}
-- p_movements: [{date, description, amount}]  (cargos faltantes en la app, monto positivo)
-- p_installments: [{purchase_date, description, original_amount, current, total}]
-- security invoker: RLS decide; solo el dueño de la tarjeta puede importar.
create function public.import_statement(
  p_card         uuid,
  p_statement    jsonb,
  p_movements    jsonb default '[]'::jsonb,
  p_installments jsonb default '[]'::jsonb
) returns jsonb
language plpgsql security invoker
as $$
declare
  v_uid     uuid := (select auth.uid());
  v_card    public.cards%rowtype;
  v_cutoff  date := (p_statement ->> 'cutoff_date')::date;
  v_due     date := nullif(p_statement ->> 'due_date', '')::date;
  v_limit   numeric := nullif(p_statement ->> 'credit_limit', '')::numeric;
  v_last4   text := nullif(p_statement ->> 'card_last4', '');
  v_hash    text;
  v_offset  int;
  v_number  int;
  v_mov     int := 0;
  v_ins     int := 0;
  v_rows    int;
  r         record;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;
  if v_cutoff is null then
    raise exception 'cutoff_date is required' using errcode = '22023';
  end if;

  select * into v_card from public.cards where id = p_card and owner_id = v_uid;
  if not found then
    raise exception 'card not found' using errcode = 'P0002';
  end if;

  -- 1) El banco manda: corte, pago y límite de la tarjeta se ajustan al estado de cuenta.
  update public.cards
     set cutoff_day   = extract(day from v_cutoff)::int,
         due_day      = coalesce(extract(day from v_due)::int, due_day),
         credit_limit = coalesce(v_limit, credit_limit),
         alt_last4    = case
                          when v_last4 is not null and v_last4 <> last4 and not (v_last4 = any (alt_last4))
                          then array_append(alt_last4, v_last4)
                          else alt_last4
                        end
   where id = p_card
  returning * into v_card;

  -- 2) Registro del estado (reimportar el mismo corte lo actualiza).
  insert into public.statements (
    household_id, owner_id, card_id, bank, card_last4, period_start, period_end,
    cutoff_date, due_date, credit_limit, pay_no_interest, minimum_payment, file_path
  ) values (
    v_card.household_id, v_uid, p_card,
    p_statement ->> 'bank', v_last4,
    nullif(p_statement ->> 'period_start', '')::date, nullif(p_statement ->> 'period_end', '')::date,
    v_cutoff, v_due, v_limit,
    nullif(p_statement ->> 'pay_no_interest', '')::numeric,
    nullif(p_statement ->> 'minimum_payment', '')::numeric,
    nullif(p_statement ->> 'file_path', '')
  )
  on conflict (owner_id, card_id, cutoff_date) do update
    set bank            = coalesce(excluded.bank, public.statements.bank),
        card_last4      = coalesce(excluded.card_last4, public.statements.card_last4),
        period_start    = coalesce(excluded.period_start, public.statements.period_start),
        period_end      = coalesce(excluded.period_end, public.statements.period_end),
        due_date        = coalesce(excluded.due_date, public.statements.due_date),
        credit_limit    = coalesce(excluded.credit_limit, public.statements.credit_limit),
        pay_no_interest = coalesce(excluded.pay_no_interest, public.statements.pay_no_interest),
        minimum_payment = coalesce(excluded.minimum_payment, public.statements.minimum_payment),
        file_path       = coalesce(excluded.file_path, public.statements.file_path),
        imported_at     = now();

  -- 3) Movimientos faltantes -> gastos confirmados de origen "statement".
  --    La clave incluye el número de ocurrencia para no fusionar dos cargos idénticos legítimos.
  for r in
    select (e ->> 'date')::date                  as op_date,
           trim(e ->> 'description')             as descr,
           (e ->> 'amount')::numeric             as amt,
           row_number() over (
             partition by e ->> 'date', e ->> 'amount', public.normalize_merchant(e ->> 'description')
             order by ord
           )                                     as occurrence
    from jsonb_array_elements(p_movements) with ordinality as t(e, ord)
  loop
    continue when r.amt is null or r.amt <= 0 or r.descr is null or r.descr = '';
    v_hash := 'stmt:' || p_card || ':' || r.op_date || ':' || r.amt || ':'
              || public.normalize_merchant(r.descr) || ':' || r.occurrence;
    insert into public.transactions (
      household_id, owner_id, card_id, merchant, amount, occurred_at,
      source, status, merchant_key, dedup_hash
    )
    select v_card.household_id, v_uid, p_card, r.descr, r.amt, r.op_date,
           'statement', 'confirmed', public.normalize_merchant(r.descr), v_hash
    where not exists (
      select 1 from public.transactions t
      where t.household_id = v_card.household_id and t.dedup_hash = v_hash
    );
    get diagnostics v_rows = row_count;
    v_mov := v_mov + v_rows;
  end loop;

  -- 4) Compras a meses -> MSI con la mensualidad que corresponde a ESTE corte.
  --    installment_number = n_del_estado - (meses entre el ciclo de la compra y el del estado).
  for r in
    select (e ->> 'purchase_date')::date         as purchase_date,
           trim(e ->> 'description')             as descr,
           (e ->> 'original_amount')::numeric    as original_amount,
           (e ->> 'current')::int                as cur,
           (e ->> 'total')::int                  as total
    from jsonb_array_elements(p_installments) as t(e)
  loop
    continue when r.original_amount is null or r.original_amount <= 0
                  or r.total is null or r.total < 2 or r.cur is null or r.descr is null or r.descr = '';

    select (extract(year from v_cutoff)::int * 12 + extract(month from v_cutoff)::int)
         - (extract(year from c.cycle_end)::int * 12 + extract(month from c.cycle_end)::int)
      into v_offset
    from public.card_cycle(v_card.cutoff_day, v_card.due_day, r.purchase_date) c;

    v_number := greatest(0, least(r.total, r.cur - v_offset));
    v_hash := 'stmt-msi:' || p_card || ':' || r.purchase_date || ':' || r.original_amount || ':'
              || public.normalize_merchant(r.descr) || ':' || r.total;

    insert into public.transactions (
      household_id, owner_id, card_id, merchant, amount, occurred_at,
      payment_mode, installments, installment_number,
      source, status, merchant_key, dedup_hash
    )
    select v_card.household_id, v_uid, p_card, r.descr, r.original_amount, r.purchase_date,
           'msi', r.total, v_number,
           'statement', 'confirmed', public.normalize_merchant(r.descr), v_hash
    where not exists (
      select 1 from public.transactions t
      where t.household_id = v_card.household_id and t.dedup_hash = v_hash
    );
    get diagnostics v_rows = row_count;
    v_ins := v_ins + v_rows;
  end loop;

  return jsonb_build_object('movements', v_mov, 'installments', v_ins);
end $$;

revoke all on function public.import_statement(uuid, jsonb, jsonb, jsonb) from public, anon;
grant execute on function public.import_statement(uuid, jsonb, jsonb, jsonb) to authenticated;
