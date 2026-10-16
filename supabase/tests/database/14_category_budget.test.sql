begin;
select plan(16);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('a1a1a1a1-0000-0000-0000-000000000014', 'cb-a@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('b2b2b2b2-0000-0000-0000-000000000014', 'cb-b@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('c3c3c3c3-0000-0000-0000-000000000014', 'cb-c@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

create or replace function pg_temp.act_as_postgres() returns void language plpgsql as $$
begin
  reset role;
end $$;

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000014');
create temp table ctx as select public.create_household('Casa', 'A') as hid;
grant all on ctx to authenticated;
create temp table inv as select public.create_invite((select hid from ctx)) as code;
grant all on inv to authenticated;

select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000014');
select public.accept_invite((select code from inv), 'B');

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000014');
create temp table cat as
select
  (select id from public.categories where name = 'Comida y restaurantes') as food,
  (select id from public.categories where name = 'Gasolina') as fuel;
grant all on cat to authenticated;

-- Octubre 2026, comida de A: 300 + 100 (1200 a 12 meses) + 50 en efectivo = 450
insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, category_id, status, source)
values ((select hid from ctx), auth.uid(), 'Taqueria', 300, '2026-10-05', (select food from cat), 'confirmed', 'manual');
insert into public.transactions
  (household_id, owner_id, merchant, amount, occurred_at, category_id, status, source, payment_mode, installments, installment_number)
values ((select hid from ctx), auth.uid(), 'Cena a meses', 1200, '2026-10-06', (select food from cat), 'confirmed', 'manual', 'msi', 12, 1);
-- No cuentan: pendiente de revisar, otro mes
insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, category_id, status, source)
values
  ((select hid from ctx), auth.uid(), 'Pendiente', 999, '2026-10-08', (select food from cat), 'pending_review', 'notification'),
  ((select hid from ctx), auth.uid(), 'Septiembre', 500, '2026-09-30', (select food from cat), 'confirmed', 'manual');
insert into public.manual_payments (household_id, owner_id, method, amount, paid_at, concept, category_id)
values ((select hid from ctx), auth.uid(), 'cash', 50, '2026-10-07 14:00:00-06', 'Tamales', (select food from cat));

-- La pareja también gasta en comida: 200 (y no debe sumarse al gasto de A)
select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000014');
insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, category_id, status, source)
values ((select hid from ctx), auth.uid(), 'Restaurante B', 200, '2026-10-09', (select food from cat), 'confirmed', 'manual');

-- 1) A: su gasto del mes suma lo suyo (gasto confirmado, mensualidad MSI y efectivo), no lo de B
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000014');
select is(
  (select spent from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  450.00::numeric, 'A ve solo su gasto: 300 + mensualidad MSI 100 + efectivo 50');

-- 2) Otro mes cuenta solo lo suyo
select is(
  (select spent from public.category_month_spend('2026-09-20') where category_id = (select food from cat)),
  500.00::numeric, 'septiembre solo cuenta lo de septiembre');

-- 3) Una categoría sin movimientos devuelve 0 y sin presupuesto
select is(
  (select row(spent, budget, pct)::text from public.category_month_spend('2026-10-20') where category_id = (select fuel from cat)),
  '(0.00,,)', 'sin gasto ni presupuesto: 0 y vacío');

-- 4) B ve solo el suyo
select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000014');
select is(
  (select spent from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  200.00::numeric, 'B ve solo su gasto: 200');

-- 4b) Las categorías y el gasto del hogar son de los dos: B también ve lo que gastó A
select is(
  (select household_spent from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  650.00::numeric, 'B ve el gasto de los dos en la categoría: 450 de A + 200 suyos');
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000014');
select is(
  (select row(spent, household_spent)::text from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  '(450.00,650.00)', 'A ve lo suyo y lo de los dos, sin mezclarlos');

-- 5) A fija su presupuesto y ve el porcentaje
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000014');
select public.set_category_budget((select food from cat), 1000);
select is(
  (select row(budget, pct)::text from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  '(1000.00,45.0)', 'A: el porcentaje se calcula contra su presupuesto');

-- 6) B no ve el presupuesto de A; fija el suyo y es independiente
select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000014');
select is(
  (select budget from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  null::numeric, 'B no hereda el presupuesto de A');
select public.set_category_budget((select food from cat), 500);
select is(
  (select row(budget, pct)::text from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  '(500.00,40.0)', 'B fija el suyo: 200 de 500');

-- 7) El de A sigue igual y B no puede leer la tabla de A
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000014');
select is(
  (select budget from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  1000.00::numeric, 'el presupuesto de A no cambió por lo de B');

select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000014');
select is((select count(*)::int from public.category_budgets), 1, 'B solo puede leer su propio presupuesto');

-- 8) El presupuesto debe ser positivo
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000014');
select throws_ok(
  $$select public.set_category_budget((select food from cat), 0)$$,
  '23514', null, 'el presupuesto debe ser mayor a cero');

-- 8b) Se puede quitar (y no toca el de B)
select public.set_category_budget((select food from cat), null);
select is(
  (select budget from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  null::numeric, 'quitar el presupuesto lo deja vacío');
select pg_temp.act_as_postgres();
select is((select count(*)::int from public.category_budgets), 1, 'solo se borró el de A; el de B sigue');

-- 9) Un extraño no ve categorías ni puede fijar presupuesto en un hogar ajeno
select pg_temp.act_as('c3c3c3c3-0000-0000-0000-000000000014');
select is((select count(*)::int from public.category_month_spend('2026-10-20')), 0,
  'un extraño no ve categorías de otro hogar');
select throws_ok(
  $$select public.set_category_budget((select food from cat), 5)$$,
  'P0002', null, 'un extraño no puede fijar presupuesto en un hogar ajeno');

select * from finish();
rollback;
