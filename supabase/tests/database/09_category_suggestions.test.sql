begin;
select plan(13);

insert into auth.users (id, email, instance_id, aud, role)
values ('cccccccc-0000-0000-0000-000000000001', 'sugerencias@test.mx', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');

create or replace function pg_temp.act_as(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;

select pg_temp.act_as('cccccccc-0000-0000-0000-000000000001');
create temp table ctx as select public.create_household('Casa', 'Yo') as hid;
grant all on ctx to authenticated;

-- Inserta un cargo de estado de cuenta y devuelve el nombre de la categoría resultante.
create or replace function pg_temp.cat_of(p_merchant text, p_source public.tx_source default 'statement')
returns text language plpgsql as $$
declare v_id uuid;
begin
  insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, source, status)
  values ((select hid from ctx), auth.uid(), p_merchant, 100, '2026-10-02', p_source, 'confirmed')
  returning id into v_id;
  return (select c.name from public.transactions t join public.categories c on c.id = t.category_id where t.id = v_id);
end $$;

-- 1) Reglas por palabra clave (comercios reales de los estados)
select is(pg_temp.cat_of('TELCEL RECARGA'),        'Servicios y suscripciones', 'Telcel es servicios y suscripciones');
select is(pg_temp.cat_of('Caffenio 🛒'),            'Cafeterías',               'Caffenio es cafeterías');
select is(pg_temp.cat_of('DANY TAQUERIA'),         'Comida y restaurantes',    'una taquería es comida');
select is(pg_temp.cat_of('MISC LA ALEMANA SION'),  'Supermercado',             'la miscelánea es supermercado');
select is(pg_temp.cat_of('STR*AMAZONPRIMESUBS'),   'Servicios y suscripciones', 'Amazon Prime es suscripción, no compra');
select is(pg_temp.cat_of('AMAZON MX'),             'Compras en línea',         'Amazon es compra en línea');
select is(pg_temp.cat_of('PAYPAL *XSOLLA TWITCH'), 'Juegos y entretenimiento', 'Twitch gana sobre PayPal');
select is(pg_temp.cat_of('UBER EATS'),             'Comida y restaurantes',    'Uber Eats es comida, no transporte');
select is(pg_temp.cat_of('UBER TRIP'),             'Transporte',               'Uber es transporte');

-- 2) Lo desconocido queda sin categoría y lo manual no se toca
select is(pg_temp.cat_of('XYZ 123 SA DE CV'), null, 'comercio desconocido queda sin categoría');
select is(pg_temp.cat_of('TELCEL', 'manual'), null, 'un gasto manual conserva lo que la persona eligió');

-- 3) Aprende del historial: si ya se categorizó, se respeta esa categoría
update public.transactions set category_id = (select id from public.categories where household_id = (select hid from ctx) and name = 'Regalos')
 where merchant = 'ZORRO LOCO';
insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, source, status, category_id)
values ((select hid from ctx), auth.uid(), 'ZORRO LOCO', 10, '2026-09-01', 'manual', 'confirmed',
        (select id from public.categories where household_id = (select hid from ctx) and name = 'Regalos'));
select is(pg_temp.cat_of('ZORRO LOCO'), 'Regalos', 'aprende de la categoría que ya se usó');

-- 4) apply_category_suggestions categoriza lo que quedó sin categoría
insert into public.transactions (household_id, owner_id, merchant, amount, occurred_at, source, status)
values ((select hid from ctx), auth.uid(), 'CAFFENIO', 50, '2026-10-03', 'manual', 'confirmed');
select is(
  (select public.apply_category_suggestions((select hid from ctx))),
  2, 'categoriza los gastos sin categoría que reconoce (Telcel y Caffenio manuales)');

select * from finish();
rollback;
