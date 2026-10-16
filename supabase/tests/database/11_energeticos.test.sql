begin;
select plan(3);

insert into auth.users (id, email, instance_id, aud, role)
values ('eeeeeeee-0000-0000-0000-000000000001', 'energeticos@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

select pg_temp.act_as('eeeeeeee-0000-0000-0000-000000000001');
create temp table ctx as select public.create_household('Casa', 'Yo') as hid;
grant all on ctx to authenticated;

create or replace function pg_temp.cat_of(p_merchant text) returns text language plpgsql as $$
declare v_id uuid;
begin
  insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, source, status)
  values ((select hid from ctx), auth.uid(), p_merchant, 219.08, current_date, 'notification', 'pending_review')
  returning id into v_id;
  return (select c.name from public.transactions t join public.categories c on c.id = t.category_id where t.id = v_id);
end $$;

select is(pg_temp.cat_of('ENERGETICOS ALTIPLANO'), 'Gasolina', 'una gasolinera "energéticos" es gasolina');
select is(pg_temp.cat_of('Energéticos del Bajío'), 'Gasolina', 'con acento y minúsculas también');
select is(pg_temp.cat_of('PEMEX 5521'), 'Gasolina', 'Pemex sigue siendo gasolina');

select * from finish();
rollback;
