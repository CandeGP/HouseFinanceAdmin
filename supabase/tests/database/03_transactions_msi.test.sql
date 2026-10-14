begin;
select plan(9);

insert into auth.users (id, email, instance_id, aud, role)
values ('aaaaaaaa-0000-0000-0000-000000000001', 'yo@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

select set_config('request.jwt.claims', '{"sub":"aaaaaaaa-0000-0000-0000-000000000001","role":"authenticated"}', true);
set local role authenticated;

create temp table ctx as select public.create_household('Casa', 'Yo') as hid;
grant all on ctx to authenticated;

insert into public.cards (id, household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
values ('11111111-1111-1111-1111-111111111111', (select hid from ctx), auth.uid(), 'Tarjeta A', 'Banco X', '1234', 10000, 15, 5);

-- Constraints
select throws_ok(
  $$insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, payment_mode)
    values ((select hid from ctx), auth.uid(), 'X', 0, '2026-10-10', 'contado')$$,
  '23514', null, 'el monto debe ser positivo');

select throws_ok(
  $$insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, payment_mode)
    values ((select hid from ctx), auth.uid(), 'X', 100, '2026-10-10', 'msi')$$,
  '23514', null, 'MSI exige número de mensualidades');

select throws_ok(
  $$insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, payment_mode, installments, installment_number)
    values ((select hid from ctx), auth.uid(), 'X', 100, '2026-10-10', 'msi', 6, 7)$$,
  '23514', null, 'la mensualidad actual no puede exceder el total');

select throws_ok(
  $$insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, payment_mode, installments)
    values ((select hid from ctx), auth.uid(), 'X', 100, '2026-10-10', 'contado', 6)$$,
  '23514', null, 'contado no puede llevar mensualidades');

-- Datos: ciclo actual de la tarjeta A (corte 15): 2026-09-16 .. 2026-10-15
-- Laptop: MSI 12 meses de 1200, comprada 2026-09-10 (ciclo previo, mensualidad #3) -> en este ciclo va la #4: 100
-- Audifonos: MSI 3 meses de 300, comprada en el ciclo actual (#1): 100
insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, payment_mode, installments, installment_number)
values
  ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'Gasolina',  500,  '2026-10-01', 'contado', null, null),
  ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'Super',     700,  '2026-09-20', 'contado', null, null),
  ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'Fuera',     999,  '2026-09-10', 'contado', null, null),
  ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'Laptop',   1200,  '2026-09-10', 'msi', 12, 3),
  ((select hid from ctx), auth.uid(), '11111111-1111-1111-1111-111111111111', 'Audifonos', 300, '2026-10-02', 'msi', 3, 1);

select is(
  (select cycle_end from public.card_cycle_estimate('11111111-1111-1111-1111-111111111111', date '2026-10-10')),
  date '2026-10-15', 'estimate: fin de ciclo');

select is(
  (select cycle_contado_total from public.card_cycle_estimate('11111111-1111-1111-1111-111111111111', date '2026-10-10')),
  1200::numeric, 'estimate: contado del ciclo = 500 + 700');

select is(
  (select cycle_msi_total from public.card_cycle_estimate('11111111-1111-1111-1111-111111111111', date '2026-10-10')),
  200::numeric, 'estimate: mensualidades MSI que caen en el ciclo');

select is(
  (select cycle_total from public.card_cycle_estimate('11111111-1111-1111-1111-111111111111', date '2026-10-10')),
  1400::numeric, 'estimate: total del ciclo = contado + MSI');

select is(
  (select usage_pct from public.card_cycle_estimate('11111111-1111-1111-1111-111111111111', date '2026-10-10')),
  round(((1400 + (1200::numeric/12)*(12-4) + (300::numeric/3)*(3-1)) / 10000) * 100, 2),
  'estimate: % de uso = (ciclo + saldo MSI pendiente) / límite');

select * from finish();
rollback;
