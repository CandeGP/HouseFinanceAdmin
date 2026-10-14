-- Captura automática de cobros (notificaciones / correo / estados de cuenta).
-- El mismo cobro suele llegar por varias fuentes (app del banco + Google Wallet): la función
-- `ingest_capture` lo deduplica por comercio normalizado + monto + ventana de 10 minutos y,
-- si la segunda fuente trae los últimos 4 dígitos, completa la tarjeta de la primera.

alter table public.transactions
  add column captured_at  timestamptz,
  add column capture_raw  text,
  add column merchant_key text;

create index tx_capture_dedup_idx
  on public.transactions (household_id, owner_id, merchant_key, amount, captured_at)
  where captured_at is not null;

-- Minúsculas, sin acentos ni símbolos/emojis: "Misc La Alemana Sión 🛒" = "MISC LA ALEMANA SION".
create function public.normalize_merchant(p text) returns text
language sql immutable
as $$
  select lower(regexp_replace(
    translate(coalesce(p, ''), 'áéíóúÁÉÍÓÚñÑüÜ', 'aeiouaeiounnuu'),
    '[^a-zA-Z0-9]', '', 'g'));
$$;

-- security invoker: las políticas RLS de `transactions` y `cards` aplican a quien llama.
create function public.ingest_capture(
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

  -- Serializa capturas del mismo comercio de la misma persona (dos notificaciones casi simultáneas).
  perform pg_advisory_xact_lock(hashtextextended(v_uid::text || ':' || v_key, 0));

  if p_card_last4 is not null then
    select c.id into v_card
    from public.cards c
    where c.household_id = p_household and c.owner_id = v_uid
      and c.last4 = p_card_last4 and not c.archived
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
    if v_existing.card_id is null and v_card is not null then
      update public.transactions set card_id = v_card where id = v_existing.id;
      return query select v_existing.id, true, v_card;
    else
      return query select v_existing.id, true, v_existing.card_id;
    end if;
    return;
  end if;

  insert into public.transactions (
    household_id, owner_id, card_id, merchant, amount, occurred_at,
    source, status, captured_at, capture_raw, merchant_key
  ) values (
    p_household, v_uid, v_card, trim(p_merchant), p_amount,
    (p_captured_at at time zone 'America/Mexico_City')::date,
    p_source, 'pending_review', p_captured_at, p_raw, v_key
  )
  returning id into v_new_id;

  return query select v_new_id, false, v_card;
end $$;

revoke all on function public.ingest_capture(uuid, text, numeric, timestamptz, text, public.tx_source, text) from public, anon;
grant execute on function public.ingest_capture(uuid, text, numeric, timestamptz, text, public.tx_source, text) to authenticated;
