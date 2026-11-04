begin;
select plan(11);

-- Cada intento real es una llamada independiente; este ayudante las hace una por una y cuenta las que pasan.
create or replace function pg_temp.burst(p_key text, p_capacity int, p_refill double precision, p_n int)
returns int language plpgsql as $$
declare r record; ok int := 0; i int;
begin
  for i in 1..p_n loop
    select * into r from public.take_token(p_key, p_capacity, p_refill);
    if r.allowed then ok := ok + 1; end if;
  end loop;
  return ok;
end $$;

-- 1) Una cubeta nueva está llena: el primer intento pasa y deja 11 fichas.
select is(
  (select tokens_left::int from public.take_token('user:t1', 12, 0.2)),
  11, 'el primer intento deja 11 de 12 fichas');

-- 2) Los siguientes 11 intentos también pasan (12 en total).
select is(
  pg_temp.burst('user:t1', 12, 0.2, 11),
  11, 'los otros 11 intentos pasan');

-- 3) El intento 13 se rechaza.
select is(
  (select allowed from public.take_token('user:t1', 12, 0.2)),
  false, 'el intento 13 se rechaza');

-- 4) Y avisa que falta poco menos de 5 s (1 ficha a 0.2 fichas/s = 5 s).
select ok(
  (select retry_after from public.take_token('user:t1', 12, 0.2)) between 4 and 5.1,
  'informa cuánto falta para la siguiente ficha (~5 s)');

-- 5) Recarga PARCIAL: tras 5 s regresa una ficha, no las 12.
update public.rate_limit_buckets set updated_at = updated_at - interval '5 seconds' where key = 'user:t1';
select is(
  (select allowed from public.take_token('user:t1', 12, 0.2)),
  true, 'a los 5 s regresa una ficha y el intento pasa');
select is(
  (select allowed from public.take_token('user:t1', 12, 0.2)),
  false, 'pero solo una: el siguiente se rechaza otra vez');

-- 6) Tras mucho tiempo la cubeta vuelve a estar llena y nunca pasa de su capacidad.
update public.rate_limit_buckets set updated_at = updated_at - interval '10 minutes' where key = 'user:t1';
select ok(
  (select tokens_left from public.take_token('user:t1', 12, 0.2)) <= 11.001,
  'después de mucho tiempo no pasa de la capacidad (queda en 11 tras gastar una)');

-- 7) La cubeta por IP: 30 de capacidad, el 31 se rechaza, y las claves no se mezclan.
select is(
  pg_temp.burst('ip:203.0.113.7', 30, 1.5, 30),
  30, 'una IP puede hacer 30 intentos seguidos');
select is(
  (select allowed from public.take_token('ip:203.0.113.7', 30, 1.5)),
  false, 'el intento 31 de esa IP se rechaza');
select is(
  (select allowed from public.take_token('ip:198.51.100.9', 30, 1.5)),
  true, 'otra IP no se ve afectada');

-- 8) Ni anon ni authenticated pueden gastar fichas (así nadie vacía la cubeta de otra persona).
set local role anon;
select throws_ok(
  $$ select * from public.take_token('user:t1', 12, 0.2) $$,
  '42501', null, 'anon no puede llamar take_token');

select * from finish();
rollback;
