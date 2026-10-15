-- Categorías por defecto ampliadas a 16 (se agregan cafeterías, viajes, mascotas y regalos).
-- Una sola función de siembra sirve a los hogares nuevos y a los que ya existen.

create function public.seed_default_categories(p_household uuid) returns void
language sql security definer set search_path = ''
as $$
  insert into public.categories (household_id, name, icon)
  select p_household, c.name, c.icon
  from (values
    ('Gasolina', 'fuel'), ('Comida y restaurantes', 'utensils'), ('Cafeterías', 'coffee'),
    ('Supermercado', 'cart'), ('Juegos y entretenimiento', 'gamepad'), ('Ropa', 'shirt'),
    ('Compras en línea', 'package'), ('Servicios y suscripciones', 'repeat'), ('Salud', 'heart'),
    ('Transporte', 'car'), ('Hogar', 'home'), ('Educación', 'book'), ('Viajes', 'plane'),
    ('Mascotas', 'paw'), ('Regalos', 'gift'), ('Otros', 'tag')
  ) as c(name, icon)
  on conflict (household_id, name) do nothing;
$$;

-- Solo la usan create_household (security definer) y esta migración.
revoke execute on function public.seed_default_categories(uuid) from public, anon, authenticated;

create or replace function public.create_household(p_name text, p_display_name text) returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  v_hid uuid;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;

  insert into public.households (name) values (p_name) returning id into v_hid;
  insert into public.members (household_id, user_id, role, display_name)
    values (v_hid, v_uid, 'admin', p_display_name);

  perform public.seed_default_categories(v_hid);

  return v_hid;
end $$;

-- Hogares que ya existían reciben las categorías nuevas sin tocar las suyas.
select public.seed_default_categories(id) from public.households;
