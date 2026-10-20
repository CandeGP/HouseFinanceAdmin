-- Valida un código de invitación antes de crear la cuenta (aún sin sesión). Devuelve el nombre del hogar si el
-- código sirve (existe, sin usar, vigente y el hogar tiene lugar); null en cualquier otro caso.
create function public.check_invite(p_code text) returns text
language sql stable security definer set search_path = ''
as $$
  select h.name
  from public.household_invites i
  join public.households h on h.id = i.household_id
  where i.code = upper(trim(p_code))
    and i.used_at is null
    and i.expires_at > now()
    and (select count(*) from public.members m where m.household_id = h.id) < h.max_members;
$$;

revoke all on function public.check_invite(text) from public;
grant execute on function public.check_invite(text) to anon, authenticated;
