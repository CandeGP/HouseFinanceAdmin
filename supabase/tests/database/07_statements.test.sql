begin;
select plan(15);

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

-- Tarjeta registrada con datos aproximados (corte 15 / pago 5 / límite 10 000)
select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
insert into public.cards (id, household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
values ('11111111-1111-1111-1111-111111111111', (select hid from ctx), auth.uid(), 'Plata', 'Plata', '5841', 10000, 15, 5);

-- 1) installment_number = 0 ya es válido (primera mensualidad en el corte siguiente)
select lives_ok(
  $$insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, payment_mode, installments, installment_number)
    values ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'Cero', 300, '2026-09-10', 'msi', 3, 0)$$,
  'installment_number = 0 es válido');

select throws_ok(
  $$insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, payment_mode, installments, installment_number)
    values ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'Neg', 300, '2026-09-10', 'msi', 3, -1)$$,
  '23514', null, 'installment_number negativo sigue inválido');

-- Con corte 15: la compra del 10-sep cierra en 15-sep (mensualidad 0 => no cobra); en el ciclo siguiente va la #1
select is(
  (select cycle_msi_total from public.card_cycle_estimate('11111111-1111-1111-1111-111111111111', date '2026-09-12')),
  0::numeric, 'mensualidad 0: el ciclo de la compra no cobra nada');

select is(
  (select cycle_msi_total from public.card_cycle_estimate('11111111-1111-1111-1111-111111111111', date '2026-10-10')),
  100::numeric, 'mensualidad 0: el ciclo siguiente cobra la primera');

delete from public.transactions where merchant = 'Cero';

-- 2) Importación: ajusta la tarjeta, registra el estado, movimientos y MSI
create temp table res as
select public.import_statement(
  '11111111-1111-1111-1111-111111111111',
  '{"bank":"Plata","card_last4":"0931","period_start":"2026-08-19","period_end":"2026-09-18",
    "cutoff_date":"2026-09-18","due_date":"2026-10-19","credit_limit":15000,
    "pay_no_interest":3075.07,"minimum_payment":200}'::jsonb,
  '[{"date":"2026-08-18","description":"PAYPAL *EJEMPLO","amount":69.60},
    {"date":"2026-09-04","description":"PP*EJEMPLO FACEBOOK","amount":79.00},
    {"date":"2026-09-04","description":"PP*EJEMPLO FACEBOOK","amount":79.00}]'::jsonb,
  '[{"purchase_date":"2026-06-22","description":"TIENDA EJEMPLO LEON","original_amount":2034,"current":2,"total":3}]'::jsonb
) as r;
grant all on res to authenticated;

select is((select (r ->> 'movements')::int from res), 3, 'importó 3 movimientos (incluye dos cargos idénticos)');
select is((select (r ->> 'installments')::int from res), 1, 'importó 1 compra a meses');

select is(
  (select cutoff_day::text || '/' || due_day::text || '/' || credit_limit::text
     from public.cards where id = '11111111-1111-1111-1111-111111111111'),
  '18/19/15000.00', 'la tarjeta quedó con corte, pago y límite del estado de cuenta');

select is(
  (select alt_last4 from public.cards where id = '11111111-1111-1111-1111-111111111111'),
  array['0931'], 'el número del estado se guardó como alias');

select is((select count(*)::int from public.statements), 1, 'quedó registrado un estado de cuenta');

-- La compra del 22-jun con corte 18: ciclo de la compra cierra 18-jul; estado cierra 18-sep (2 meses);
-- el estado dice pago 2 => installment_number = 0
select is(
  (select installment_number from public.transactions where merchant = 'TIENDA EJEMPLO LEON'),
  0, 'la mensualidad se calcula para que este corte muestre el número del estado');

select is(
  (select status::text || '/' || source::text from public.transactions where merchant = 'TIENDA EJEMPLO LEON'),
  'confirmed/statement', 'lo importado queda confirmado y de origen estado de cuenta');

-- 3) Idempotencia: importar de nuevo no duplica
select is(
  (select (public.import_statement(
      '11111111-1111-1111-1111-111111111111',
      '{"cutoff_date":"2026-09-18","due_date":"2026-10-19","credit_limit":15000}'::jsonb,
      '[{"date":"2026-08-18","description":"PAYPAL *EJEMPLO","amount":69.60}]'::jsonb,
      '[{"purchase_date":"2026-06-22","description":"TIENDA EJEMPLO LEON","original_amount":2034,"current":2,"total":3}]'::jsonb
    ) ->> 'movements')::int),
  0, 'reimportar no duplica movimientos');

select is((select count(*)::int from public.transactions), 4, 'siguen 4 transacciones (3 movimientos + 1 MSI)');
select is((select count(*)::int from public.statements), 1, 'sigue un solo estado para ese corte');

-- 4) Solo el dueño de la tarjeta importa
select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
select throws_ok(
  $$select public.import_statement('11111111-1111-1111-1111-111111111111', '{"cutoff_date":"2026-09-18"}'::jsonb)$$,
  'P0002', null, 'la pareja no puede importar a la tarjeta de otra persona');

select * from finish();
rollback;
