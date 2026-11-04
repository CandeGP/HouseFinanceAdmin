-- Rate limit del login con "token bucket" (cubeta de fichas).
--
-- Cada cubeta tiene una capacidad y se rellena de forma CONTINUA: no es una ventana que se reinicia, sino
-- fichas que van regresando poco a poco. Gastar una ficha = un intento. Sin fichas, el intento se rechaza
-- y se informa en cuántos segundos vuelve la siguiente.
--
-- El login usa dos cubetas por intento (ver supabase/functions/login):
--   ip:<ip>     capacidad 30, recarga 30 fichas / 20 s  = 1.5 fichas por segundo
--   user:<hash> capacidad 12, recarga 12 fichas / 60 s  = 0.2 fichas por segundo
--
-- Solo la Edge Function (service_role) puede gastar fichas: si cualquiera pudiera llamar la función, podría
-- vaciar la cubeta de otra persona y dejarla fuera.

create table public.rate_limit_buckets (
  key        text primary key,
  tokens     double precision not null,
  updated_at timestamptz not null default clock_timestamp()
);

-- Sin políticas: nadie con llave anon/authenticated ve ni toca estas filas.
alter table public.rate_limit_buckets enable row level security;
revoke all on public.rate_limit_buckets from public, anon, authenticated;

create or replace function public.take_token(
  p_key            text,
  p_capacity       integer,
  p_refill_per_sec double precision
)
returns table (allowed boolean, retry_after double precision, tokens_left double precision)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_now     timestamptz := clock_timestamp();
  v_tokens  double precision;
  v_updated timestamptz;
begin
  if p_key is null or length(p_key) = 0 or length(p_key) > 200 then
    raise exception 'invalid rate limit key';
  end if;
  if p_capacity < 1 or p_refill_per_sec <= 0 then
    raise exception 'invalid rate limit parameters';
  end if;

  -- Una cubeta nueva empieza llena. El bloqueo de fila serializa los intentos simultáneos de la misma clave.
  insert into public.rate_limit_buckets (key, tokens, updated_at)
  values (p_key, p_capacity, v_now)
  on conflict (key) do nothing;

  select b.tokens, b.updated_at into v_tokens, v_updated
  from public.rate_limit_buckets b
  where b.key = p_key
  for update;

  -- Recarga continua, sin pasar de la capacidad.
  v_tokens := least(
    p_capacity::double precision,
    v_tokens + greatest(0, extract(epoch from (v_now - v_updated))) * p_refill_per_sec
  );

  -- Limpieza ocasional: una cubeta sin uso por una hora ya está llena, igual que una nueva.
  if random() < 0.01 then
    delete from public.rate_limit_buckets where updated_at < v_now - interval '1 hour' and key <> p_key;
  end if;

  if v_tokens >= 1 then
    v_tokens := v_tokens - 1;
    update public.rate_limit_buckets set tokens = v_tokens, updated_at = v_now where key = p_key;
    return query select true, 0::double precision, v_tokens;
    return;
  end if;

  update public.rate_limit_buckets set tokens = v_tokens, updated_at = v_now where key = p_key;
  return query select false, (1 - v_tokens) / p_refill_per_sec, v_tokens;
end;
$$;

revoke all on function public.take_token(text, integer, double precision) from public, anon, authenticated;
grant execute on function public.take_token(text, integer, double precision) to service_role;
