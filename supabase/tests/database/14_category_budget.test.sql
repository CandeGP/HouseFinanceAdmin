begin;
select plan(10);

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

-- Octubre 2026, categoría comida: 300 + 100 (1200 a 12 meses) + 50 en efectivo = 450
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

-- 1) El gasto del mes suma gasto confirmado, mensualidad MSI y pago en efectivo
select is(
  (select spent from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  450.00::numeric, 'suma gastos confirmados, la mensualidad MSI y los pagos en efectivo');

-- 2) Otro mes cuenta solo lo suyo
select is(
  (select spent from public.category_month_spend('2026-09-20') where category_id = (select food from cat)),
  500.00::numeric, 'septiembre solo cuenta lo de septiembre');

-- 3) Una categoría sin movimientos devuelve 0 y sin presupuesto
select is(
  (select row(spent, budget, pct)::text from public.category_month_spend('2026-10-20') where category_id = (select fuel from cat)),
  '(0.00,,)', 'sin gasto ni presupuesto: 0 y vacío');

-- 4) Fijar presupuesto y ver el porcentaje (A)
update public.categories set monthly_budget = 1000 where id = (select food from cat);
select is(
  (select row(budget, pct)::text from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  '(1000.00,45.0)', 'el porcentaje se calcula contra el presupuesto');

-- 5) La pareja también puede cambiarlo (es del hogar)
select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000014');
update public.categories set monthly_budget = 600 where id = (select food from cat);
select pg_temp.act_as_postgres();
select is((select monthly_budget from public.categories where id = (select food from cat)), 600.00::numeric,
  'cualquiera de los dos miembros puede fijar el presupuesto');

-- 6) El presupuesto debe ser positivo
select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000014');
select throws_ok(
  $$update public.categories set monthly_budget = 0 where id = (select food from cat)$$,
  '23514', null, 'el presupuesto debe ser mayor a cero');

-- 7) Se puede quitar
update public.categories set monthly_budget = null where id = (select food from cat);
select is((select budget from public.category_month_spend('2026-10-20') where category_id = (select food from cat)),
  null::numeric, 'quitar el presupuesto lo deja vacío');

-- 8) Un extraño no ve categorías ni gasto del hogar
select pg_temp.act_as('c3c3c3c3-0000-0000-0000-000000000014');
select is((select count(*)::int from public.category_month_spend('2026-10-20')), 0,
  'un extraño no ve categorías de otro hogar');

-- 9) Un extraño no puede cambiar presupuestos ajenos
update public.categories set monthly_budget = 1 where id = (select food from cat);
select pg_temp.act_as_postgres();
select isnt((select monthly_budget from public.categories where id = (select food from cat)), 1.00::numeric,
  'un extraño no pudo cambiar el presupuesto');

-- 10) Los pagos a tarjeta y préstamos no cuentan como gasto de categoría
select is(
  (select sum(spent) from public.category_month_spend('2026-10-20') c
     join public.categories k on k.id = c.category_id where k.household_id = (select hid from ctx)),
  450.00::numeric, 'solo hay gasto de comida; nada más se cuela');

select * from finish();
rollback;
