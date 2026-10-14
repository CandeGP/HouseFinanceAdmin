begin;
select plan(11);

-- card_cycle(cutoff_day, due_day, ref) -> (cycle_start, cycle_end, due_date)
-- Convención: la compra hecha el día de corte cae en el ciclo que cierra ese día.

select is(
  (select cycle_end from public.card_cycle(15, 5, date '2026-10-10')),
  date '2026-10-15', 'antes del corte: el ciclo cierra este mes');

select is(
  (select cycle_start from public.card_cycle(15, 5, date '2026-10-10')),
  date '2026-09-16', 'antes del corte: el ciclo inicia el día después del corte anterior');

select is(
  (select cycle_end from public.card_cycle(15, 5, date '2026-10-15')),
  date '2026-10-15', 'el día de corte pertenece al ciclo que cierra');

select is(
  (select cycle_end from public.card_cycle(15, 5, date '2026-10-16')),
  date '2026-11-15', 'después del corte: el ciclo cierra el mes siguiente');

select is(
  (select due_date from public.card_cycle(15, 5, date '2026-10-10')),
  date '2026-11-05', 'pago menor al corte: cae el mes siguiente al corte');

select is(
  (select due_date from public.card_cycle(15, 28, date '2026-10-10')),
  date '2026-10-28', 'pago mayor al corte: cae el mismo mes del corte');

select is(
  (select cycle_end from public.card_cycle(31, 20, date '2026-02-10')),
  date '2026-02-28', 'corte 31 en febrero se ajusta al último día del mes');

select is(
  (select cycle_start from public.card_cycle(31, 20, date '2026-02-10')),
  date '2026-02-01', 'ciclo de febrero con corte 31 inicia el 1 de febrero');

select is(
  (select cycle_end from public.card_cycle(29, 10, date '2028-02-20')),
  date '2028-02-29', 'corte 29 en año bisiesto');

select is(
  (select cycle_start from public.card_cycle(10, 1, date '2026-01-05')),
  date '2025-12-11', 'cruce de año: el ciclo inicia en diciembre');

select throws_ok(
  $$select * from public.card_cycle(0, 5, date '2026-10-10')$$,
  '22023', null, 'rechaza día de corte fuera de 1..31');

select * from finish();
rollback;
