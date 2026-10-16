begin;
select plan(13);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('a1a1a1a1-0000-0000-0000-000000000013', 'ln-a@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('b2b2b2b2-0000-0000-0000-000000000013', 'ln-b@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('c3c3c3c3-0000-0000-0000-000000000013', 'ln-c@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

create or replace function pg_temp.act_as_postgres() returns void language plpgsql as $$
begin
  reset role;
end $$;

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000013');
create temp table ctx as select public.create_household('Casa', 'A') as hid;
grant all on ctx to authenticated;
create temp table inv as select public.create_invite((select hid from ctx)) as code;
grant all on inv to authenticated;

select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000013');
select public.accept_invite((select code from inv), 'B');

-- 1) A presta 300 a su hermana
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000013');
create temp table loan_ctx as
with ins as (
  insert into public.loans (household_id, owner_id, person, direction, amount, method)
  values ((select hid from ctx), auth.uid(), 'Hermana', 'lent', 300, 'transfer')
  returning id
)
select id from ins;
grant all on loan_ctx to authenticated;

select is((select count(*)::int from public.loans), 1, 'A registra un préstamo');

-- 2) Restricciones
select throws_ok(
  $$insert into public.loans (household_id, owner_id, person, direction, amount, method)
    values ((select hid from ctx), auth.uid(), 'X', 'lent', 0, 'cash')$$,
  '23514', null, 'el monto debe ser positivo');

select throws_ok(
  $$insert into public.loans (household_id, owner_id, person, direction, amount, method)
    values ((select hid from ctx), auth.uid(), '   ', 'lent', 10, 'cash')$$,
  '23514', null, 'la persona no puede estar vacía');

-- 3) Abonos: parcial, luego uno que se pasa del saldo
select lives_ok(
  $$insert into public.loan_repayments (household_id, owner_id, loan_id, amount, method)
    values ((select hid from ctx), auth.uid(), (select id from loan_ctx), 100, 'cash')$$,
  'A registra un abono parcial');

select throws_ok(
  $$insert into public.loan_repayments (household_id, owner_id, loan_id, amount, method)
    values ((select hid from ctx), auth.uid(), (select id from loan_ctx), 250, 'cash')$$,
  '23514', null, 'un abono no puede pasarse de lo que falta');

select lives_ok(
  $$insert into public.loan_repayments (household_id, owner_id, loan_id, amount, method)
    values ((select hid from ctx), auth.uid(), (select id from loan_ctx), 200, 'transfer')$$,
  'se puede abonar exactamente lo que falta');

-- 4) B lee, pero no escribe en lo de A (un segundo préstamo abierto, para que el límite de abonos no estorbe)
create temp table loan_ctx2 as
with ins as (
  insert into public.loans (household_id, owner_id, person, direction, amount, method)
  values ((select hid from ctx), auth.uid(), 'Mamá', 'lent', 50, 'cash')
  returning id
)
select id from ins;
grant all on loan_ctx2 to authenticated;

select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000013');
select is((select count(*)::int from public.loans), 2, 'la pareja ve los préstamos del hogar');

select throws_ok(
  $$insert into public.loan_repayments (household_id, owner_id, loan_id, amount, method)
    values ((select hid from ctx), auth.uid(), (select id from loan_ctx2), 1, 'cash')$$,
  '42501', null, 'la pareja no puede abonar a un préstamo ajeno');

update public.loans set forgiven_at = now();
select pg_temp.act_as_postgres();
select is((select count(*)::int from public.loans where forgiven_at is not null), 0,
  'la pareja no pudo perdonar los préstamos de A');

select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000013');
delete from public.loans;
select pg_temp.act_as_postgres();
select is((select count(*)::int from public.loans), 2, 'la pareja no pudo borrar los préstamos de A');

-- 5) Un extraño no ve nada
select pg_temp.act_as('c3c3c3c3-0000-0000-0000-000000000013');
select is((select count(*)::int from public.loans), 0, 'un extraño no ve préstamos');

-- 6) A perdona y luego borra; los abonos se van con el préstamo
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000013');
update public.loans set forgiven_at = now();
select is((select count(*)::int from public.loans where forgiven_at is not null), 2, 'A perdona sus préstamos');

delete from public.loans;
select pg_temp.act_as_postgres();
select is((select count(*)::int from public.loan_repayments), 0, 'al borrar el préstamo se borran sus abonos');

select * from finish();
rollback;
