begin;
select plan(8);

insert into auth.users (id, email, instance_id, aud, role)
values ('dddddddd-0000-0000-0000-000000000001', 'presupuesto@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

select pg_temp.act_as('dddddddd-0000-0000-0000-000000000001');
create temp table ctx as select public.create_household('Casa', 'Yo') as hid;
grant all on ctx to authenticated;

insert into public.cards (id, household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day, monthly_budget)
values ('11111111-1111-1111-1111-111111111111', (select hid from ctx), auth.uid(), 'Plata', 'Plata', '0931', 15000, 18, 19, 2000),
       ('22222222-2222-2222-2222-222222222222', (select hid from ctx), auth.uid(), 'BBVA', 'BBVA', '9876', 40000, 25, 14, null);

-- Este mes: 1500 contado confirmado + MSI de 1200 en 6 pagos (cuenta 200) + 600 por revisar (no cuenta)
-- Mes pasado: 900 (no cuenta)
insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, payment_mode, installments, installment_number, status, source)
values
  ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'WALMART',  1500, current_date, 'contado', null, null, 'confirmed',      'manual'),
  ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'LIVERPOOL', 1200, current_date, 'msi',     6,    1,    'confirmed',      'manual'),
  ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'OXXO',       600, current_date, 'contado', null, null, 'pending_review', 'notification'),
  ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'COSTCO',     900, (date_trunc('month', current_date) - interval '1 day')::date, 'contado', null, null, 'confirmed', 'manual');

-- 1) Suma contado + mensualidad MSI; ignora lo pendiente y otros meses
select is((select spent from public.card_month_spend('11111111-1111-1111-1111-111111111111')), 1700.00::numeric,
          'cuenta contado y la mensualidad MSI, sin pendientes ni otros meses');
select is((select budget from public.card_month_spend('11111111-1111-1111-1111-111111111111')), 2000.00::numeric,
          'devuelve el presupuesto de la tarjeta');
select is((select pct from public.card_month_spend('11111111-1111-1111-1111-111111111111')), 85.0::numeric,
          '1700 de 2000 es 85%');

-- 2) Al pasarse del presupuesto el porcentaje supera 100
insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, status, source)
values ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'AMAZON', 400, current_date, 'confirmed', 'manual');
select is((select pct from public.card_month_spend('11111111-1111-1111-1111-111111111111')), 105.0::numeric,
          '2100 de 2000 es 105%');

-- 3) Sin presupuesto: se informa el gasto pero no hay porcentaje
select is((select budget from public.card_month_spend('22222222-2222-2222-2222-222222222222')), null::numeric,
          'una tarjeta sin presupuesto devuelve budget nulo');
select is((select pct from public.card_month_spend('22222222-2222-2222-2222-222222222222')), null::numeric,
          'y porcentaje nulo');

-- 4) Se consulta otro mes con la fecha de referencia
select is((select spent from public.card_month_spend('11111111-1111-1111-1111-111111111111',
            (date_trunc('month', current_date) - interval '1 day')::date)), 900.00::numeric,
          'con otra fecha de referencia suma ese mes');

-- 5) Un presupuesto debe ser positivo
select throws_ok(
  $$update public.cards set monthly_budget = 0 where id = '11111111-1111-1111-1111-111111111111'$$,
  '23514', null, 'el presupuesto debe ser mayor a cero');

select * from finish();
rollback;
