begin;
select plan(14);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'yo@test.mx',     '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'pareja@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('cccccccc-0000-0000-0000-000000000003', 'extra@test.mx',  '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

create or replace function pg_temp.act_as_postgres() returns void language plpgsql as $$
begin
  reset role;
end $$;

-- Hogar de A y B
select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
create temp table ctx as select public.create_household('Casa', 'Yo') as hid;
grant all on ctx to authenticated;
create temp table inv as select public.create_invite((select hid from ctx)) as code;
grant all on inv to authenticated;

select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
select public.accept_invite((select code from inv), 'Pareja');

-- 1) A registra un pago en efectivo con evidencia
select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
select lives_ok(
  $$insert into public.manual_payments
      (household_id, owner_id, method, amount, paid_at, concept, evidence_path)
    values ((select hid from ctx), auth.uid(), 'cash', 350, '2026-10-12 14:30:00-06', 'Mecánico',
            (select hid from ctx)::text || '/aaaaaaaa-0000-0000-0000-000000000001/e1.jpg')$$,
  'A registra un pago en efectivo con evidencia');

-- 2) Restricciones
select throws_ok(
  $$insert into public.manual_payments (household_id, owner_id, method, amount, paid_at, concept)
    values ((select hid from ctx), auth.uid(), 'transfer', 0, now(), 'X')$$,
  '23514', null, 'el monto debe ser positivo');

select throws_ok(
  $$insert into public.manual_payments (household_id, owner_id, method, amount, paid_at, concept)
    values ((select hid from ctx), auth.uid(), 'transfer', 10, now(), '  ')$$,
  '23514', null, 'el concepto no puede estar vacío');

select throws_ok(
  $$insert into public.manual_payments (household_id, owner_id, method, amount, paid_at, concept, evidence_path)
    values ((select hid from ctx), auth.uid(), 'transfer', 10, now(), 'X',
            'otro-hogar/otro-dueno/e.jpg')$$,
  '23514', null, 'la evidencia debe vivir en la carpeta del hogar y del dueño');

-- 3) B lee pero no modifica
select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
select is((select count(*)::int from public.manual_payments), 1, 'la pareja ve los pagos del hogar');

update public.manual_payments set amount = 1 where concept = 'Mecánico';
select pg_temp.act_as_postgres();
select is((select amount from public.manual_payments where concept = 'Mecánico'), 350::numeric,
  'la pareja no pudo modificar el pago');

select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
delete from public.manual_payments where concept = 'Mecánico';
select pg_temp.act_as_postgres();
select is((select count(*)::int from public.manual_payments), 1, 'la pareja no pudo borrar el pago');

select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
select throws_ok(
  $$insert into public.manual_payments (household_id, owner_id, method, amount, paid_at, concept)
    values ((select hid from ctx), 'aaaaaaaa-0000-0000-0000-000000000001', 'cash', 5, now(), 'Falso')$$,
  '42501', null, 'no se puede registrar un pago a nombre de otra persona');

-- 4) Un extraño no ve nada
select pg_temp.act_as('cccccccc-0000-0000-0000-000000000003');
select is((select count(*)::int from public.manual_payments), 0, 'un extraño no ve pagos');

-- 5) Storage: bucket privado y políticas por carpeta
select pg_temp.act_as_postgres();
select is((select public from storage.buckets where id = 'evidence'), false, 'el bucket de evidencias es privado');

select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
select lives_ok(
  $$insert into storage.objects (bucket_id, name, owner)
    values ('evidence', (select hid from ctx)::text || '/aaaaaaaa-0000-0000-0000-000000000001/e1.jpg', auth.uid())$$,
  'A sube evidencia a su carpeta');

select pg_temp.act_as('bbbbbbbb-0000-0000-0000-000000000002');
select is((select count(*)::int from storage.objects where bucket_id = 'evidence'), 1,
  'la pareja puede ver la evidencia del hogar');

select throws_ok(
  $$insert into storage.objects (bucket_id, name, owner)
    values ('evidence', (select hid from ctx)::text || '/aaaaaaaa-0000-0000-0000-000000000001/falso.jpg', auth.uid())$$,
  '42501', null, 'la pareja no puede subir a la carpeta de A');

select pg_temp.act_as('cccccccc-0000-0000-0000-000000000003');
select is((select count(*)::int from storage.objects where bucket_id = 'evidence'), 0,
  'un extraño no ve evidencias');

select * from finish();
rollback;
