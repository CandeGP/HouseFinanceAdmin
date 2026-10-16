-- Editar abonos de préstamos y pagos a tarjeta (antes solo se podía borrar y volver a registrar).
-- Misma regla de siempre: el hogar lee todo y solo quien lo creó puede cambiarlo.

create policy cp_update on public.card_payments for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (
    owner_id = (select auth.uid())
    and public.is_household_member(household_id)
    and exists (select 1 from public.cards c where c.id = card_id and c.owner_id = (select auth.uid()))
  );

create policy lr_update on public.loan_repayments for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (
    owner_id = (select auth.uid())
    and public.is_household_member(household_id)
    and exists (select 1 from public.loans l where l.id = loan_id and l.owner_id = (select auth.uid()))
  );

-- El tope de abonos también aplica al editar: se suman los demás abonos del préstamo, sin contar
-- el que se está cambiando (en un insert, `new.id` ya trae su valor por defecto y no coincide con ninguno).
create or replace function public.loan_repayment_cap() returns trigger
language plpgsql security invoker
as $$
declare
  v_amount numeric;
  v_paid   numeric;
begin
  select l.amount into v_amount from public.loans l where l.id = new.loan_id;
  select coalesce(sum(r.amount), 0) into v_paid
    from public.loan_repayments r
   where r.loan_id = new.loan_id and r.id <> new.id;
  if v_paid + new.amount > v_amount then
    raise exception 'repayment exceeds the loan amount' using errcode = '23514';
  end if;
  return new;
end $$;

drop trigger lr_cap on public.loan_repayments;
create trigger lr_cap before insert or update on public.loan_repayments
  for each row execute function public.loan_repayment_cap();
