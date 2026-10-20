begin;
select plan(4);

insert into auth.users (id, email, instance_id, aud, role)
values
  ('a1a1a1a1-0000-0000-0000-000000000019', 'ci-a@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
  ('b2b2b2b2-0000-0000-0000-000000000019', 'ci-b@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

select pg_temp.act_as('a1a1a1a1-0000-0000-0000-000000000019');
create temp table ctx as select public.create_household('Casa Chida', 'A') as hid;
grant all on ctx to authenticated;
create temp table inv as select public.create_invite((select hid from ctx)) as code;
grant all on inv to anon, authenticated;

-- Sin sesión (anon): el código bueno devuelve el nombre del hogar, en minúsculas también.
reset role;
set local role anon;
select is(public.check_invite((select code from inv)), 'Casa Chida', 'código válido devuelve el hogar');
select is(public.check_invite(lower((select code from inv))), 'Casa Chida', 'no distingue mayúsculas');
select is(public.check_invite('NOEXISTE00'), null, 'código inexistente devuelve null');

-- Una vez usado deja de servir.
select pg_temp.act_as('b2b2b2b2-0000-0000-0000-000000000019');
select public.accept_invite((select code from inv), 'B');
reset role;
set local role anon;
select is(public.check_invite((select code from inv)), null, 'código usado devuelve null');

select * from finish();
rollback;
