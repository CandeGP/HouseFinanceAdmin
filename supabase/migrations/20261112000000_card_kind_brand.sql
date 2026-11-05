-- Tipo (crédito/débito) y marca (Visa, Mastercard, Amex) de la tarjeta. Aditivo: las tarjetas existentes
-- quedan como crédito sin marca. Débito no usa límite, corte ni pago: la app guarda 0 / 1 / 1 en esas columnas.
alter table public.cards
  add column kind  text not null default 'credit' check (kind in ('credit', 'debit')),
  add column brand text check (brand in ('visa', 'mastercard', 'amex'));
