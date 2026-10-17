begin;
select plan(7);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('a1a1a1a1-0000-0000-0000-000000000018', 'ms-a@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('c3c3c3c3-0000-0000-0000-000000000018', 'ms-c@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000018');
create temp table ctx as select public.create_household('Casa', 'A') as hid;
grant all on ctx to authenticated;

-- Corte el 10, pago el 25.
create temp table card_ctx as
with ins as (
  insert into public.cards (household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
  values ((select hid from ctx), auth.uid(), 'Uno', 'BBVA', '1111', 50000, 10, 25)
  returning id
)
select id from ins;
grant all on card_ctx to authenticated;

-- Compra de $300 a 3 meses hecha el 20 sep (ciclo 11 sep – 10 oct), mensualidad 1 en ese ciclo.
insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, status, source,
                                 payment_mode, installments, installment_number)
values ((select hid from ctx), auth.uid(), (select id from card_ctx), 'Tienda', 300, '2026-09-20', 'confirmed', 'manual', 'msi', 3, 1);

-- 1) Desde el 20 oct (ciclo abierto 11 oct – 10 nov) faltan la 2 y la 3
select is(
  (select count(*)::int from public.msi_schedule('2026-10-20', 12) where merchant = 'Tienda'),
  2, 'faltan dos mensualidades');

select is(
  (select row(installment_number, installments, due_date, amount)::text
     from public.msi_schedule('2026-10-20', 12) where merchant = 'Tienda' order by due_date limit 1),
  '(2,3,2026-11-25,100.00)', 'la primera que falta es la 2 de 3, se paga el 25 de noviembre');

select is(
  (select row(installment_number, due_date)::text
     from public.msi_schedule('2026-10-20', 12) where merchant = 'Tienda' order by due_date desc limit 1),
  '(3,2026-12-25)', 'la última es la 3 de 3, se paga el 25 de diciembre');

-- 2) Una compra de contado no aparece
insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, status, source)
values ((select hid from ctx), auth.uid(), (select id from card_ctx), 'Contado', 500, '2026-10-12', 'confirmed', 'manual');
select is(
  (select count(*)::int from public.msi_schedule('2026-10-20', 12) where merchant = 'Contado'),
  0, 'lo de contado no es una mensualidad');

-- 3) Con mensualidad 0 (empieza el corte siguiente) la primera cae un ciclo después
insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, status, source,
                                 payment_mode, installments, installment_number)
values ((select hid from ctx), auth.uid(), (select id from card_ctx), 'Diferida', 200, '2026-10-15', 'confirmed', 'manual', 'msi', 2, 0);
select is(
  (select row(installment_number, due_date)::text
     from public.msi_schedule('2026-10-20', 12) where merchant = 'Diferida' order by due_date limit 1),
  '(1,2026-12-25)', 'con mensualidad 0 la primera se paga un ciclo después');

-- 4) Una tarjeta archivada sigue proyectando lo que falta pagar de sus compras (2 de Tienda + 2 de Diferida)
update public.cards set archived = true where id = (select id from card_ctx);
select is(
  (select count(*)::int from public.msi_schedule('2026-10-20', 12)),
  4, 'una tarjeta archivada sigue proyectando sus mensualidades pendientes');

-- 5) Un extraño no ve el calendario del hogar
select pg_temp.act_as('c3c3c3c3-0000-0000-0000-000000000018');
select is((select count(*)::int from public.msi_schedule('2026-10-20', 12)), 0, 'un extraño no ve mensualidades ajenas');

select * from finish();
rollback;
