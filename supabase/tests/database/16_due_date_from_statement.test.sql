begin;
select plan(4);

insert into auth.users (id, email, instance_id, aud, role)
values ('a1a1a1a1-0000-0000-0000-000000000016', 'dd-a@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000016');
create temp table ctx as select public.create_household('Casa', 'A') as hid;
grant all on ctx to authenticated;

-- Corta el 18 y "paga el 19": sin estado, la app supone el día siguiente al corte.
create temp table card_ctx as
with ins as (
  insert into public.cards (household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
  values ((select hid from ctx), auth.uid(), 'Plata', 'Plata', '0931', 15000, 18, 19)
  returning id
)
select id from ins;
grant all on card_ctx to authenticated;

-- Ciclo cerrado de ref 2026-10-05: 19 ago – 18 sep.
select is(
  (select due_date from public.card_cycle_status((select id from card_ctx), '2026-10-05')),
  '2026-09-19'::date, 'sin estado de cuenta: el pago cae el día siguiente al corte');

insert into public.statements (household_id, owner_id, card_id, cutoff_date, due_date, pay_no_interest)
values ((select hid from ctx), auth.uid(), (select id from card_ctx), '2026-09-18', '2026-10-19', 3075.07);

select is(
  (select due_date from public.card_cycle_status((select id from card_ctx), '2026-10-05')),
  '2026-10-19'::date, 'con estado de cuenta manda su fecha límite');

select is(
  (select due_date from public.card_cycle_estimate((select id from card_ctx), '2026-09-10')),
  '2026-10-19'::date, 'el ciclo del estado también usa su fecha límite');

-- Ciclo abierto (19 sep – 18 oct): copia el desfase de un mes del último estado.
select is(
  (select due_date from public.card_cycle_estimate((select id from card_ctx), '2026-10-05')),
  '2026-11-19'::date, 'sin estado del ciclo se copia el desfase en meses del último estado');

select * from finish();
rollback;
