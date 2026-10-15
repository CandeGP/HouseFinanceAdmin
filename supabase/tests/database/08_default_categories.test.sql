begin;
select plan(6);

insert into auth.users (id, email, instance_id, aud, role)
values ('aaaaaaaa-0000-0000-0000-000000000001', 'categorias@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

select pg_temp.act_as('aaaaaaaa-0000-0000-0000-000000000001');
create temp table ctx as select public.create_household('Casa', 'Yo') as hid;
grant all on ctx to authenticated;

-- 1) Un hogar nuevo trae las 16 categorías por defecto
select is(
  (select count(*)::int from public.categories where household_id = (select hid from ctx)),
  16, 'un hogar nuevo trae 16 categorías');

-- 2) Incluye las que se agregaron para validar etiquetado
select is(
  (select count(*)::int from public.categories
   where household_id = (select hid from ctx)
     and name in ('Cafeterías', 'Viajes', 'Mascotas', 'Regalos')),
  4, 'incluye cafeterías, viajes, mascotas y regalos');

-- 3) Ninguna categoría repetida
select is(
  (select count(distinct name)::int from public.categories where household_id = (select hid from ctx)),
  16, 'los nombres son únicos');

-- 4) La función de siembra no la puede llamar una persona autenticada
select throws_ok(
  $$select public.seed_default_categories((select hid from ctx))$$,
  '42501', null, 'seed_default_categories no es invocable por authenticated');

-- 5) Sembrar de nuevo es idempotente y no pisa categorías renombradas
reset role;
update public.categories set icon = 'custom' where household_id = (select hid from ctx) and name = 'Hogar';
select public.seed_default_categories((select hid from ctx));

select is(
  (select count(*)::int from public.categories where household_id = (select hid from ctx)),
  16, 'sembrar otra vez no duplica');

select is(
  (select icon from public.categories where household_id = (select hid from ctx) and name = 'Hogar'),
  'custom', 'sembrar otra vez conserva los cambios');

select * from finish();
rollback;
