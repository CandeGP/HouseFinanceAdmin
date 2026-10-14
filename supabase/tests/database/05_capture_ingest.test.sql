begin;
select plan(14);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'yo@test.mx',    '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('cccccccc-0000-0000-0000-000000000003', 'extra@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
create temp table ctx as select public.create_household('Casa', 'Yo') as hid;
grant all on ctx to authenticated;

insert into public.cards (id, household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
values ('11111111-1111-1111-1111-111111111111', (select hid from ctx), auth.uid(), 'Plata', 'Plata', '4321', 15000, 18, 19);

-- 1) Normalización de comercio: acentos, emojis, mayúsculas y símbolos no cuentan
select is(
  public.normalize_merchant('Misc La Alemana Sión 🛒'),
  public.normalize_merchant('MISC LA ALEMANA SION'),
  'normalize_merchant ignora acentos, emojis y mayúsculas');

-- 2) Primer cobro (app del banco): sin tarjeta conocida, pendiente de revisión
select is(
  (select duplicate from public.ingest_capture((select hid from ctx), 'Caffenio', 127, '2026-10-14T17:39:00Z', null, 'notification', 'plata')),
  false, 'el primer cobro no es duplicado');

select is(
  (select status::text || '/' || source::text || '/' || coalesce(card_id::text, 'sin-tarjeta')
     from public.transactions where merchant = 'Caffenio'),
  'pending_review/notification/sin-tarjeta', 'queda pendiente de revisión, de origen notificación y sin tarjeta');

-- 3) El mismo cobro por Google Wallet (mayúsculas, 30 s después, con últimos 4): duplicado que enriquece la tarjeta
select is(
  (select duplicate from public.ingest_capture((select hid from ctx), 'CAFFENIO', 127, '2026-10-14T17:39:30Z', '4321', 'notification', 'wallet')),
  true, 'el mismo cobro desde otra fuente se detecta como duplicado');

select is((select count(*)::int from public.transactions), 1, 'sigue habiendo una sola fila');

select is(
  (select card_id from public.transactions where merchant = 'Caffenio'),
  '11111111-1111-1111-1111-111111111111'::uuid,
  'el duplicado completó la tarjeta usando los últimos 4 dígitos');

-- 4) Otro monto = otro cobro
select is(
  (select duplicate from public.ingest_capture((select hid from ctx), 'Caffenio', 90, '2026-10-14T17:40:00Z', null, 'notification', 'plata')),
  false, 'mismo comercio con otro monto no es duplicado');

-- 5) Mismo comercio y monto pero 11 minutos después = otro cobro (dos cafés)
select is(
  (select duplicate from public.ingest_capture((select hid from ctx), 'Caffenio', 127, '2026-10-14T17:50:30Z', null, 'notification', 'plata')),
  false, 'fuera de la ventana de 10 minutos no es duplicado');

-- 6) Últimos 4 desconocidos: no inventa tarjeta
select is(
  (select resolved_card_id from public.ingest_capture((select hid from ctx), 'Uber Eats', 250, '2026-10-14T18:00:00Z', '9999', 'notification', 'wallet')),
  null::uuid, 'últimos 4 sin tarjeta registrada quedan sin tarjeta');

-- 7) La fecha del gasto es la de Ciudad de México (03:00 UTC del 15 = 21:00 del 14)
select lives_ok(
  $$select * from public.ingest_capture((select hid from ctx), 'Madrugada', 10, '2026-10-15T03:00:00Z', null, 'notification', 'plata')$$,
  'captura de madrugada UTC');

select is(
  (select occurred_at from public.transactions where merchant = 'Madrugada'),
  date '2026-10-14', 'occurred_at usa la zona horaria de México');

-- 8) Monto inválido
select throws_ok(
  $$select * from public.ingest_capture((select hid from ctx), 'X', 0, now(), null, 'notification', null)$$,
  '22023', null, 'monto no positivo se rechaza');

-- 9) Un tercero no puede inyectar cobros en el hogar
select pg_temp.act_as('cccccccc-0000-0000-0000-000000000003');
select throws_ok(
  $$select * from public.ingest_capture((select hid from ctx), 'Intruso', 5, now(), null, 'notification', null)$$,
  '42501', null, 'un extraño no puede capturar en el hogar');

select is((select count(*)::int from public.transactions), 0, 'y no ve las capturas del hogar');

select * from finish();
rollback;
