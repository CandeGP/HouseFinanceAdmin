begin;
select plan(11);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'yo@test.mx',     '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'pareja@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
create temp table ctx as select public.create_household('Casa', 'Yo') as hid;
grant all on ctx to authenticated;
create temp table inv as select public.create_invite((select hid from ctx)) as code;
grant all on inv to authenticated;

select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
select public.accept_invite((select code from inv), 'Pareja');

-- Tarjeta física 0931; Google Wallet reporta 5841 (número tokenizado)
select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
insert into public.cards (id, household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
values ('11111111-1111-1111-1111-111111111111', (select hid from ctx), auth.uid(), 'Plata', 'Plata', '0931', 15000, 18, 19);

-- 1) Formato de alias
select throws_ok(
  $$update public.cards set alt_last4 = array['58x1'] where id = '11111111-1111-1111-1111-111111111111'$$,
  '23514', null, 'los alias deben ser 4 dígitos');

-- 2) Captura con últimos 4 desconocidos: queda sin tarjeta pero recuerda el número
select is(
  (select resolved_card_id from public.ingest_capture((select hid from ctx), 'Caffenio', 127, '2026-10-14T17:39:00Z', '5841', 'notification', 'wallet')),
  null::uuid, 'últimos 4 desconocidos no resuelven tarjeta');

select is(
  (select capture_card_last4 from public.transactions where merchant = 'Caffenio'),
  '5841', 'la captura recuerda con qué últimos 4 llegó');

-- 3) Confirmar enseña el alias
select lives_ok(
  $$select public.confirm_capture((select id from public.transactions where merchant = 'Caffenio'),
                                  '11111111-1111-1111-1111-111111111111', null)$$,
  'confirmar el cobro con la tarjeta Plata');

select is(
  (select alt_last4 from public.cards where id = '11111111-1111-1111-1111-111111111111'),
  array['5841'], 'la tarjeta aprendió el alias 5841');

select is(
  (select status::text from public.transactions where merchant = 'Caffenio'),
  'confirmed', 'el cobro quedó confirmado');

-- 4) Siguiente captura con el alias se resuelve sola
select is(
  (select resolved_card_id from public.ingest_capture((select hid from ctx), 'Uber Eats', 250, '2026-10-14T20:00:00Z', '5841', 'notification', 'wallet')),
  '11111111-1111-1111-1111-111111111111'::uuid, 'el alias resuelve la tarjeta automáticamente');

-- 5) No duplica el alias al confirmar otra vez
select lives_ok(
  $$select public.confirm_capture((select id from public.transactions where merchant = 'Uber Eats'),
                                  '11111111-1111-1111-1111-111111111111', null)$$,
  'confirmar un segundo cobro con el mismo alias');

select is(
  (select cardinality(alt_last4) from public.cards where id = '11111111-1111-1111-1111-111111111111'),
  1, 'el alias no se duplica');

-- 6) Si los últimos 4 coinciden con el principal, no se crea alias
select public.ingest_capture((select hid from ctx), 'Oxxo', 30, '2026-10-14T21:00:00Z', '0931', 'notification', 'wallet');
select public.confirm_capture((select id from public.transactions where merchant = 'Oxxo'),
                              '11111111-1111-1111-1111-111111111111', null);
select is(
  (select cardinality(alt_last4) from public.cards where id = '11111111-1111-1111-1111-111111111111'),
  1, 'los últimos 4 principales no se agregan como alias');

-- 7) Otra persona no puede confirmar cobros ajenos
select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
select throws_ok(
  $$select public.confirm_capture((select id from public.transactions where merchant = 'Oxxo'),
                                  '11111111-1111-1111-1111-111111111111', null)$$,
  'P0002', null, 'la pareja no puede confirmar el cobro de otra persona');

select * from finish();
rollback;
