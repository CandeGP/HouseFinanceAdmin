-- Tarjeta congelada: sigue existiendo y se paga, pero no se ofrece para cobros nuevos.
alter table public.cards add column frozen boolean not null default false;
