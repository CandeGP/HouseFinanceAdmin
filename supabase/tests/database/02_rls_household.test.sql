begin;
select plan(14);

-- Usuarios de prueba (auth.users mínimo)
insert into auth.users (id, email, instance_id, aud, role)
values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'yo@test.mx',      '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'pareja@test.mx',  '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('cccccccc-0000-0000-0000-000000000003', 'extra@test.mx',   '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

create or replace function pg_temp.act_as_postgres() returns void language plpgsql as $$
begin
  reset role;
end $$;

-- 1) Yo creo el hogar e invito a mi pareja
select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
create temp table ctx as select public.create_household('Casa', 'Yo') as hid;
grant all on ctx to authenticated;

select ok((select count(*) from public.members) = 1, 'el creador queda como único miembro');
select is((select role::text from public.members limit 1), 'admin', 'el creador es admin');
select ok((select count(*) from public.categories) >= 8, 'se siembran categorías por defecto');

create temp table inv as select public.create_invite((select hid from ctx)) as code;
grant all on inv to authenticated;

insert into public.cards (household_id, owner_id, name, bank, last4, credit_limit, cutoff_day, due_day)
values ((select hid from ctx), auth.uid(), 'Tarjeta A', 'Banco X', '1234', 20000, 15, 5);

insert into public.transactions (household_id, owner_id, card_id, merchant, amount, occurred_at, payment_mode)
values ((select hid from ctx), auth.uid(), (select id from public.cards limit 1), 'Gasolinera', 500, '2026-10-10', 'contado');

-- 2) Mi pareja acepta la invitación
select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
select lives_ok($$select public.accept_invite((select code from inv), 'Pareja')$$, 'la pareja acepta la invitación');

select is((select count(*)::int from public.transactions), 1, 'la pareja ve los gastos compartidos del hogar');

-- 3) La pareja no puede editar ni borrar mis datos (RLS: 0 filas afectadas)
update public.transactions set amount = 1 where merchant = 'Gasolinera';
select pg_temp.act_as_postgres();
select is((select amount from public.transactions where merchant = 'Gasolinera'), 500::numeric,
  'la pareja no pudo modificar mi gasto');

select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
delete from public.transactions where merchant = 'Gasolinera';
select pg_temp.act_as_postgres();
select is((select count(*)::int from public.transactions where merchant = 'Gasolinera'), 1,
  'la pareja no pudo borrar mi gasto');

-- 4) La pareja no puede insertar gastos a nombre mío
select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
select throws_ok(
  $$insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, payment_mode)
    values ((select hid from ctx), 'aaaaaaaa-0000-0000-0000-000000000001', 'Falso', 10, '2026-10-10', 'contado')$$,
  '42501', null, 'no se puede insertar un gasto con owner_id ajeno');

-- 5) Pero sí puede registrar los suyos y editarlos
insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, payment_mode, notes)
values ((select hid from ctx), auth.uid(), 'Super', 900, '2026-10-11', 'contado', 'despensa');
update public.transactions set amount = 950 where merchant = 'Super';
select is((select amount from public.transactions where merchant = 'Super'), 950::numeric,
  'la pareja edita sus propios gastos');

-- 6) Un tercero no ve nada del hogar
select pg_temp.act_as('cccccccc-0000-0000-0000-000000000003');
select is((select count(*)::int from public.transactions), 0, 'un extraño no ve gastos');
select is((select count(*)::int from public.cards), 0, 'un extraño no ve tarjetas');
select is((select count(*)::int from public.members), 0, 'un extraño no ve miembros');

-- 7) Límite de 2 miembros por hogar
select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
create temp table inv2 as select public.create_invite((select hid from ctx)) as code;
grant all on inv2 to authenticated;
select pg_temp.act_as('cccccccc-0000-0000-0000-000000000003');
select throws_ok(
  $$select public.accept_invite((select code from inv2), 'Tercero')$$,
  'P0001', null, 'el hogar no admite un tercer miembro');

-- 8) Una invitación usada no se puede reutilizar
select pg_temp.act_as('cccccccc-0000-0000-0000-000000000003');
select throws_ok(
  $$select public.accept_invite((select code from inv), 'Otro')$$,
  'P0001', null, 'una invitación usada no es reutilizable');

select * from finish();
rollback;
