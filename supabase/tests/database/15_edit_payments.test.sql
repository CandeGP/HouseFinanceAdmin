begin;
select plan(10);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('a1a1a1a1-0000-0000-0000-000000000015', 'ep-a@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('b2b2b2b2-0000-0000-0000-000000000015', 'ep-b@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

create or replace function pg_temp.act_as_postgres() returns void language plpgsql as $$
begin
  reset role;
end $$;

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000015');
create temp table ctx as select public.create_household('Casa', 'A') as hid;
grant all on ctx to authenticated;
create temp table inv as select public.create_invite((select hid from ctx)) as code;
grant all on inv to authenticated;

select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000015');
select public.accept_invite((select code from inv), 'B');
-- B tiene su propia tarjeta
create temp table b_card as
with ins as (
  insert into public.cards (household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
  values ((select hid from ctx), auth.uid(), 'DeB', 'BBVA', '9999', 10000, 10, 25) returning id
) select id from ins;
grant all on b_card to authenticated;

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000015');
-- Cada pieza en su propia sentencia: las políticas no "ven" filas creadas en la misma sentencia.
create temp table a_card as
with ins as (
  insert into public.cards (household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
  values ((select hid from ctx), auth.uid(), 'DeA', 'BBVA', '1111', 10000, 10, 25) returning id
) select id from ins;
grant all on a_card to authenticated;

create temp table a_pay as
with ins as (
  insert into public.card_payments (household_id, owner_id, card_id, cutoff_date, amount, method)
  values ((select hid from ctx), auth.uid(), (select id from a_card), '2026-10-10', 100, 'cash') returning id
) select id from ins;
grant all on a_pay to authenticated;

create temp table a_loan as
with ins as (
  insert into public.loans (household_id, owner_id, person, direction, amount, method)
  values ((select hid from ctx), auth.uid(), 'Hermana', 'lent', 300, 'transfer') returning id
) select id from ins;
grant all on a_loan to authenticated;

create temp table a_rep as
with ins as (
  insert into public.loan_repayments (household_id, owner_id, loan_id, amount, method)
  values ((select hid from ctx), auth.uid(), (select id from a_loan), 100, 'cash') returning id
) select id from ins;
grant all on a_rep to authenticated;

-- 1) A edita monto y fecha de su pago a tarjeta
update public.card_payments set amount = 150, paid_at = '2026-10-01 12:00:00-06' where id = (select id from a_pay);
select is((select amount from public.card_payments where id = (select id from a_pay)), 150.00::numeric,
  'A edita el monto de su pago a tarjeta');
select is((select (paid_at at time zone 'America/Mexico_City')::date from public.card_payments where id = (select id from a_pay)),
  '2026-10-01'::date, 'y su fecha');

-- 2) El monto sigue debiendo ser positivo
select throws_ok(
  $$update public.card_payments set amount = 0 where id = (select id from a_pay)$$,
  '23514', null, 'editar no permite un monto en cero');

-- 3) A no puede mover su pago a una tarjeta ajena
select throws_ok(
  $$update public.card_payments set card_id = (select id from b_card) where id = (select id from a_pay)$$,
  '42501', null, 'no se puede ligar el pago a una tarjeta de otra persona');

-- 4) B no puede editar lo de A
select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000015');
update public.card_payments set amount = 1 where id = (select id from a_pay);
update public.loan_repayments set amount = 1 where id = (select id from a_rep);
select pg_temp.act_as_postgres();
select is(
  (select row((select amount from public.card_payments where id = (select id from a_pay)),
              (select amount from public.loan_repayments where id = (select id from a_rep)))::text),
  '(150.00,100.00)', 'la pareja no pudo editar ni el pago ni el abono de A');

-- 5) Abonos: subir hasta el tope sí, pasarse no
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000015');
update public.loan_repayments set amount = 300, paid_on = '2026-10-05' where id = (select id from a_rep);
select is((select amount from public.loan_repayments where id = (select id from a_rep)), 300.00::numeric,
  'A edita el abono hasta el total del préstamo');
select is((select paid_on from public.loan_repayments where id = (select id from a_rep)), '2026-10-05'::date,
  'y su fecha');

select throws_ok(
  $$update public.loan_repayments set amount = 301 where id = (select id from a_rep)$$,
  '23514', null, 'editar un abono no puede pasarse del préstamo');

-- 6) Con otro abono de por medio, el tope cuenta los demás (bajo el primero a 50 y agrego uno de 250)
update public.loan_repayments set amount = 50 where id = (select id from a_rep);
insert into public.loan_repayments (household_id, owner_id, loan_id, amount, method)
values ((select hid from ctx), auth.uid(), (select id from a_loan), 250, 'transfer');
select throws_ok(
  $$update public.loan_repayments set amount = 60 where id = (select id from a_rep)$$,
  '23514', null, 'el tope suma los demás abonos al editar uno');
select lives_ok(
  $$update public.loan_repayments set amount = 50, note = 'mismo monto' where id = (select id from a_rep)$$,
  'editar sin cambiar el total sí se permite');

select * from finish();
rollback;
