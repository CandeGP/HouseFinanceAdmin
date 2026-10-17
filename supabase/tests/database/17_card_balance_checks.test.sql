begin;
select plan(5);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('a1a1a1a1-0000-0000-0000-000000000017', 'bc-a@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('b2b2b2b2-0000-0000-0000-000000000017', 'bc-b@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('c3c3c3c3-0000-0000-0000-000000000017', 'bc-c@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

-- Hogar de A y B; A tiene una tarjeta.
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000017');
create temp table ctx as select public.create_household('Casa', 'A') as hid;
grant all on ctx to authenticated;
create temp table inv as select public.create_invite((select hid from ctx)) as code;
grant all on inv to authenticated;

select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000017');
select public.accept_invite((select code from inv), 'B');

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000017');
create temp table card_ctx as
with ins as (
  insert into public.cards (household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
  values ((select hid from ctx), auth.uid(), 'Uno', 'BBVA', '1111', 50000, 10, 25)
  returning id
)
select id from ins;
grant all on card_ctx to authenticated;

-- 1) La dueña captura el saldo del banco
insert into public.card_balance_checks (household_id, owner_id, card_id, bank_balance, checked_on)
values ((select hid from ctx), auth.uid(), (select id from card_ctx), 4558.95, '2026-10-16');
select is((select count(*)::int from public.card_balance_checks), 1, 'la dueña registra el saldo del banco');

-- 2) No acepta saldos negativos
select throws_ok(
  $$insert into public.card_balance_checks (household_id, owner_id, card_id, bank_balance)
    values ((select hid from ctx), auth.uid(), (select id from card_ctx), -1)$$,
  '23514', null, 'el saldo no puede ser negativo');

-- 3) La pareja ve la captura pero no puede registrar en una tarjeta ajena
select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000017');
select is((select count(*)::int from public.card_balance_checks), 1, 'la pareja ve las capturas de saldo');

select throws_ok(
  $$insert into public.card_balance_checks (household_id, owner_id, card_id, bank_balance)
    values ((select hid from ctx), auth.uid(), (select id from card_ctx), 10)$$,
  '42501', null, 'la pareja no puede registrar saldos en una tarjeta ajena');

-- 4) Un extraño no ve nada
select pg_temp.act_as('c3c3c3c3-0000-0000-0000-000000000017');
select is((select count(*)::int from public.card_balance_checks), 0, 'un extraño no ve capturas de saldo');

select * from finish();
rollback;
