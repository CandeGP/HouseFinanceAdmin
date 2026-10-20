-- Marca de las tarjetas que dan cashback / bonificaciones.
alter table public.cards add column has_cashback boolean not null default false;
