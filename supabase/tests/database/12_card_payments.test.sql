begin;
select plan(16);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('a1a1a1a1-0000-0000-0000-000000000012', 'cp-a@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('b2b2b2b2-0000-0000-0000-000000000012', 'cp-b@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('c3c3c3c3-0000-0000-0000-000000000012', 'cp-c@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

create or replace function pg_temp.act_as_postgres() returns void language plpgsql as $$
begin
  reset role;
end $$;

-- Hogar de A y B; A tiene dos tarjetas con corte día 10 y pago día 25.
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000012');
create temp table ctx as select public.create_household('Casa', 'A') as hid;
grant all on ctx to authenticated;
create temp table inv as select public.create_invite((select hid from ctx)) as code;
grant all on inv to authenticated;

select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000012');
select public.accept_invite((select code from inv), 'B');

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000012');
create temp table cards_ctx as
with ins as (
  insert into public.cards (household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
  values
    ((select hid from ctx), auth.uid(), 'Uno', 'BBVA', '1111', 50000, 10, 25),
    ((select hid from ctx), auth.uid(), 'Dos', 'BBVA', '2222', 50000, 10, 25)
  returning id, name
)
select
  (select id from ins where name = 'Uno') as uno,
  (select id from ins where name = 'Dos') as dos;
grant all on cards_ctx to authenticated;

-- Ciclo cerrado de ref 2026-10-20: 11 sep – 10 oct, límite de pago 25 oct.
insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, status, source)
values ((select hid from ctx), auth.uid(), (select uno from cards_ctx), 'Compra', 1000, '2026-09-20', 'confirmed', 'manual');

-- 1) Sin estado de cuenta ni pagos: usa la estimación y está pendiente
select is(
  (select row(target_amount, target_source, paid_amount, remaining, status)::text
     from public.card_cycle_status((select uno from cards_ctx), '2026-10-20')),
  '(1000.00,estimate,0.00,1000.00,pending)',
  'sin estado de cuenta: objetivo estimado y pendiente');

select is(
  (select cutoff_date from public.card_cycle_status((select uno from cards_ctx), '2026-10-20')),
  '2026-10-10'::date, 'evalúa el último ciclo cerrado');

-- 2) Con estado de cuenta: manda el pago para no generar intereses
insert into public.statements (household_id, owner_id, card_id, cutoff_date, pay_no_interest, minimum_payment)
values ((select hid from ctx), auth.uid(), (select uno from cards_ctx), '2026-10-10', 1407.38, 200);

select is(
  (select row(target_amount, target_source, minimum_payment)::text
     from public.card_cycle_status((select uno from cards_ctx), '2026-10-20')),
  '(1407.38,statement,200.00)', 'el estado de cuenta manda sobre la estimación');

-- 3) Pagos: por debajo del mínimo, mínimo cubierto, total
insert into public.card_payments (household_id, owner_id, card_id, cutoff_date, amount, method)
values ((select hid from ctx), auth.uid(), (select uno from cards_ctx), '2026-10-10', 100, 'cash');
select is(
  (select row(paid_amount, remaining, status)::text from public.card_cycle_status((select uno from cards_ctx), '2026-10-20')),
  '(100.00,1307.38,partial)', 'menos del mínimo es parcial');

insert into public.card_payments (household_id, owner_id, card_id, cutoff_date, amount, method)
values ((select hid from ctx), auth.uid(), (select uno from cards_ctx), '2026-10-10', 150, 'transfer');
select is(
  (select row(paid_amount, remaining, status)::text from public.card_cycle_status((select uno from cards_ctx), '2026-10-20')),
  '(250.00,1157.38,minimum_only)', 'cubrir el mínimo sin el total deja minimum_only');

insert into public.card_payments (household_id, owner_id, card_id, cutoff_date, amount, method)
values ((select hid from ctx), auth.uid(), (select uno from cards_ctx), '2026-10-10', 1157.38, 'transfer');
select is(
  (select row(paid_amount, remaining, status)::text from public.card_cycle_status((select uno from cards_ctx), '2026-10-20')),
  '(1407.38,0.00,paid)', 'completar el monto deja el ciclo pagado');

-- 4) Compras posteriores al corte no reabren el ciclo pagado
insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, status, source)
values ((select hid from ctx), auth.uid(), (select uno from cards_ctx), 'Nueva', 500, '2026-10-15', 'confirmed', 'manual');
select is(
  (select status from public.card_cycle_status((select uno from cards_ctx), '2026-10-20')),
  'paid', 'una compra del ciclo siguiente no cambia el ciclo pagado');

-- 5) Vencido solo si la tarjeta ya lleva pagos registrados; si no, sin registro
select is(
  (select status from public.card_cycle_status((select dos from cards_ctx), '2026-10-26')),
  'paid', 'sin compras en el ciclo no hay nada que pagar');

insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, status, source)
values ((select hid from ctx), auth.uid(), (select dos from cards_ctx), 'Compra', 800, '2026-09-25', 'confirmed', 'manual');
select is(
  (select status from public.card_cycle_status((select dos from cards_ctx), '2026-10-26')),
  'untracked', 'pasado el límite y sin pagos nunca registrados: sin registro');

insert into public.card_payments (household_id, owner_id, card_id, cutoff_date, amount, method)
values ((select hid from ctx), auth.uid(), (select dos from cards_ctx), '2026-08-10', 50, 'cash');
select is(
  (select status from public.card_cycle_status((select dos from cards_ctx), '2026-10-26')),
  'overdue', 'pasado el límite con pagos registrados antes: vencido');

-- 6) El excedente del ciclo anterior se suma al siguiente
insert into public.card_payments (household_id, owner_id, card_id, cutoff_date, amount, method)
values ((select hid from ctx), auth.uid(), (select dos from cards_ctx), '2026-09-10', 300, 'cash');
select is(
  (select paid_amount from public.card_cycle_status((select dos from cards_ctx), '2026-10-26')),
  300.00::numeric, 'lo pagado de más del ciclo anterior cuenta para el siguiente');

-- 7) Restricciones y privacidad
select throws_ok(
  $$insert into public.card_payments (household_id, owner_id, card_id, cutoff_date, amount, method)
    values ((select hid from ctx), auth.uid(), (select uno from cards_ctx), '2026-10-10', 0, 'cash')$$,
  '23514', null, 'el monto debe ser positivo');

select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000012');
select is((select count(*)::int from public.card_payments where card_id = (select uno from cards_ctx)), 3,
  'la pareja ve los pagos de la tarjeta');

select throws_ok(
  $$insert into public.card_payments (household_id, owner_id, card_id, cutoff_date, amount, method)
    values ((select hid from ctx), auth.uid(), (select uno from cards_ctx), '2026-10-10', 10, 'cash')$$,
  '42501', null, 'la pareja no puede registrar pagos en una tarjeta ajena');

delete from public.card_payments where card_id = (select uno from cards_ctx);
select pg_temp.act_as_postgres();
select is((select count(*)::int from public.card_payments where card_id = (select uno from cards_ctx)), 3,
  'la pareja no pudo borrar los pagos de la tarjeta ajena');

select pg_temp.act_as('c3c3c3c3-0000-0000-0000-000000000012');
select is((select count(*)::int from public.card_payments), 0, 'un extraño no ve pagos');

select * from finish();
rollback;
