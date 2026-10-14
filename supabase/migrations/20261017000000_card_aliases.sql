-- Una tarjeta puede aparecer con varios "últimos 4": el plástico, la tarjeta digital/virtual y el
-- número tokenizado de Google Wallet (p. ej. Plata: 0931 en el estado de cuenta, ••5841 en Wallet).
-- `alt_last4` guarda los alias; se aprenden solos cuando la persona confirma un cobro capturado.

alter table public.cards
  add column alt_last4 text[] not null default '{}',
  add constraint cards_alt_last4_digits
    check (array_to_string(alt_last4, ',') ~ '^([0-9]{4}(,[0-9]{4})*)?$');

alter table public.transactions
  add column capture_card_last4 text;

-- Misma firma que en la migración anterior; ahora resuelve la tarjeta también por alias
-- y recuerda los últimos 4 con los que llegó la captura.
create or replace function public.ingest_capture(
  p_household   uuid,
  p_merchant    text,
  p_amount      numeric,
  p_captured_at timestamptz,
  p_card_last4  text default null,
  p_source      public.tx_source default 'notification',
  p_raw         text default null
)
returns table (tx_id uuid, duplicate boolean, resolved_card_id uuid)
language plpgsql security invoker
as $$
declare
  v_uid      uuid := (select auth.uid());
  v_key      text := public.normalize_merchant(p_merchant);
  v_card     uuid;
  v_existing public.transactions%rowtype;
  v_new_id   uuid;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;
  if p_amount is null or p_amount <= 0 then
    raise exception 'amount must be positive' using errcode = '22023';
  end if;
  if length(v_key) = 0 then
    raise exception 'merchant is required' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(v_uid::text || ':' || v_key, 0));

  if p_card_last4 is not null then
    select c.id into v_card
    from public.cards c
    where c.household_id = p_household and c.owner_id = v_uid and not c.archived
      and (c.last4 = p_card_last4 or p_card_last4 = any (c.alt_last4))
    order by c.created_at
    limit 1;
  end if;

  select t.* into v_existing
  from public.transactions t
  where t.household_id = p_household and t.owner_id = v_uid
    and t.merchant_key = v_key and t.amount = p_amount
    and t.captured_at between p_captured_at - interval '10 minutes'
                          and p_captured_at + interval '10 minutes'
  order by abs(extract(epoch from (t.captured_at - p_captured_at)))
  limit 1;

  if found then
    if v_existing.card_id is null and (v_card is not null or p_card_last4 is not null) then
      update public.transactions
         set card_id = coalesce(v_card, card_id),
             capture_card_last4 = coalesce(capture_card_last4, p_card_last4)
       where id = v_existing.id;
      return query select v_existing.id, true, coalesce(v_card, v_existing.card_id);
    else
      return query select v_existing.id, true, v_existing.card_id;
    end if;
    return;
  end if;

  insert into public.transactions (
    household_id, owner_id, card_id, merchant, amount, occurred_at,
    source, status, captured_at, capture_raw, merchant_key, capture_card_last4
  ) values (
    p_household, v_uid, v_card, trim(p_merchant), p_amount,
    (p_captured_at at time zone 'America/Mexico_City')::date,
    p_source, 'pending_review', p_captured_at, p_raw, v_key, p_card_last4
  )
  returning id into v_new_id;

  return query select v_new_id, false, v_card;
end $$;

-- Confirma un cobro capturado y aprende el alias de tarjeta si los últimos 4 no coinciden.
create function public.confirm_capture(p_tx uuid, p_card uuid, p_category uuid default null)
returns void
language plpgsql security invoker
as $$
declare
  v_uid   uuid := (select auth.uid());
  v_last4 text;
begin
  update public.transactions
     set status = 'confirmed', card_id = p_card, category_id = p_category
   where id = p_tx and owner_id = v_uid
  returning capture_card_last4 into v_last4;

  if not found then
    raise exception 'transaction not found' using errcode = 'P0002';
  end if;

  if v_last4 is not null then
    update public.cards
       set alt_last4 = array_append(alt_last4, v_last4)
     where id = p_card and owner_id = v_uid
       and last4 <> v_last4 and not (v_last4 = any (alt_last4));
  end if;
end $$;

revoke all on function public.confirm_capture(uuid, uuid, uuid) from public, anon;
grant execute on function public.confirm_capture(uuid, uuid, uuid) to authenticated;
