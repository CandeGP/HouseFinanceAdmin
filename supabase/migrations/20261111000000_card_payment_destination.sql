-- A dónde va un pago a tarjeta:
--   cycle       el corte cerrado que se debe pagar (como siempre; cutoff_date = ese corte)
--   next_cycle  adelanto: baja el pago del ciclo abierto (cutoff_date = el corte de ese ciclo)
--   capital     abono a capital: baja lo que falta de las compras a meses; no es de ningún corte (cutoff_date null)
-- card_cycle_status sigue sumando por cutoff_date, así que los abonos a capital (null) no lo afectan.
alter table public.card_payments
  add column destination text not null default 'cycle'
    check (destination in ('cycle', 'next_cycle', 'capital'));

alter table public.card_payments alter column cutoff_date drop not null;

alter table public.card_payments
  add constraint card_payments_cutoff_by_destination
    check ((destination = 'capital') = (cutoff_date is null));
