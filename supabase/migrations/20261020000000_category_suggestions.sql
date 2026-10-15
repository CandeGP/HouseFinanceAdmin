-- Sugerencia de categoría por comercio.
-- 1) Aprende del historial del hogar: si ese comercio ya se categorizó, se repite esa categoría.
-- 2) Si no, aplica palabras clave (la primera regla que coincide gana; el orden importa).
-- Se aplica sola a cargos que llegan por estado de cuenta, notificación o correo; los gastos
-- manuales conservan lo que la persona eligió (incluido dejarlo sin categoría).

create function public.guess_category(p_household uuid, p_merchant text) returns uuid
language sql stable security invoker
as $$
  with norm as (
    select trim(regexp_replace(
             lower(translate(coalesce(p_merchant, ''), 'áéíóúÁÉÍÓÚñÑüÜ', 'aeiouaeiounnuu')),
             '[^a-z0-9]+', ' ', 'g')) as m
  ),
  history as (
    select t.category_id
    from public.transactions t
    where t.household_id = p_household
      and t.category_id is not null
      and public.normalize_merchant(t.merchant) = public.normalize_merchant(p_merchant)
    order by t.created_at desc
    limit 1
  ),
  rule as (
    select c.id
    from norm n
    join (values
      (1,  'uber eats|rappi|didi food|restaurante|taqueria|\ytacos?\y|pizza|sushi|burger|hamburguesa|mcdonald|kfc|domino|wings|mariscos|asadero|pollo|birria|fonda|panaderia|helader|vips|toks|sanborns|chilis|cocina', 'Comida y restaurantes'),
      (2,  'caffenio|starbucks|cafe\y|cafeteria|coffee|dunkin|punta del cielo|cielito', 'Cafeterías'),
      (3,  'telcel|\yat t\y|movistar|izzi|totalplay|megacable|telmex|netflix|spotify|disney|hbo|\ymax\y|prime|youtube|google one|icloud|apple com bill|microsoft|adobe|openai|chatgpt|\ycfe\y|facebook|dropbox|canva|linkedin|suscripcion', 'Servicios y suscripciones'),
      (4,  'pemex|gasolin|shell|mobil|g500|\ybp\y|repsol|hidrosina|combustible', 'Gasolina'),
      (5,  '\yuber\y|didi|cabify|taxi|indrive|\ymetro\y|autobus|\yado\y|caseta|estacionamiento|parking|flixbus|\ytren\y|peaje', 'Transporte'),
      (6,  'farmacia|benavides|similares|doctor|dental|dentista|hospital|laboratorio|clinica|consultorio|optica|chopo|olab|medic|\ysalud\y|psicolog|nutri', 'Salud'),
      (7,  'aeromexico|volaris|viva aerobus|vivaaerobus|airbnb|booking|hotel|expedia|despegar|hostal|marriott|aeropuerto|hilton|resort', 'Viajes'),
      (8,  'veterinari|mascota|petco|purina|\ypets?\y|dogtor', 'Mascotas'),
      (9,  'steam|playstation|xbox|nintendo|epic games|twitch|xsolla|blizzard|riot|roblox|cinepolis|cinemex|boleto|ticketmaster|google play|app store|\ygame', 'Juegos y entretenimiento'),
      (10, '\yzara\y|\yh m\y|bershka|nike|adidas|\yropa\y|calzado|coppel|suburbia|\yc a\y|flexi|andrea|zapater', 'Ropa'),
      (11, 'escuela|colegio|universidad|coursera|udemy|platzi|libreria|gandhi|educacion|\ycurso|colegiatura|duolingo|academia|kinder', 'Educación'),
      (12, 'home depot|homedepot|ikea|ferreteria|muebles|plomer|electricista|lowes|jardiner', 'Hogar'),
      (13, 'regalo|florer|flores|\ygift|joyeria|souvenir', 'Regalos'),
      (14, 'amazon|mercadolibre|mercado libre|mercado pago|mercadopago|aliexpress|shein|temu|ebay|paypal|liverpool|linio|shopify|etsy', 'Compras en línea'),
      (15, 'walmart|aurrera|soriana|chedraui|costco|\ysams\y|la comer|\yheb\y|oxxo|7 eleven|abarrotes|city market|miscelanea|\ymisc\y|tienda|fresko|alsuper|\ysuper\y', 'Supermercado'),
      (16, 'administracion tarjeta|comision|\yiva\y|intereses?\y|anualidad', 'Otros')
    ) as r(prio, pattern, category) on n.m ~ r.pattern
    join public.categories c on c.household_id = p_household and c.name = r.category
    order by r.prio
    limit 1
  )
  select coalesce((select category_id from history), (select id from rule));
$$;

create function public.set_default_category() returns trigger
language plpgsql security invoker
as $$
begin
  if new.category_id is null and new.source in ('statement', 'notification', 'email') then
    new.category_id := public.guess_category(new.household_id, new.merchant);
  end if;
  return new;
end $$;

create trigger transactions_default_category
  before insert on public.transactions
  for each row execute function public.set_default_category();

-- Categoriza lo que ya estaba sin categoría (solo gastos propios; RLS impide tocar los de la pareja).
-- Devuelve cuántos cambió.
create function public.apply_category_suggestions(p_household uuid) returns int
language plpgsql security invoker
as $$
declare
  v_count int;
begin
  update public.transactions t
     set category_id = public.guess_category(t.household_id, t.merchant)
   where t.household_id = p_household
     and t.owner_id = (select auth.uid())
     and t.category_id is null
     and public.guess_category(t.household_id, t.merchant) is not null;
  get diagnostics v_count = row_count;
  return v_count;
end $$;
