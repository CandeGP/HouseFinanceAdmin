import type { Card } from "./data";

// Colores de marca: el banco se reconoce de un vistazo sin leer el nombre.
const BRAND_COLORS: ReadonlyArray<readonly [RegExp, string]> = [
  [/bbva|bancomer/i, "#1464A5"],
  [/santander/i, "#EC0000"],
  [/\bnu\b|nubank/i, "#820AD1"],
  [/plata/i, "#0F9D8A"],
  [/rappi/i, "#FF441F"],
  [/banorte/i, "#EB0029"],
  [/hsbc/i, "#DB0011"],
  [/banamex|citi/i, "#056DAE"],
  [/amex|american express/i, "#006FCF"],
  [/mercado\s*pago/i, "#009EE3"],
];

// Para bancos sin color de marca: estable por tarjeta, así no cambia entre sesiones.
const FALLBACK_COLORS = ["#375390", "#0F9D8A", "#C2410C", "#7C3AED", "#4D7C0F", "#BE185D"];
const HEX = /^#[0-9a-f]{6}$/i;

export type CardIdentity = Pick<Card, "id" | "bank" | "name" | "last4" | "color">;

/** Color de la tarjeta: el que se eligió, el de su banco o uno estable derivado del id. */
export function cardAccent(card: CardIdentity): string {
  if (card.color && HEX.test(card.color)) return card.color;
  const brand = BRAND_COLORS.find(([pattern]) => pattern.test(card.bank));
  if (brand) return brand[1];
  let hash = 0;
  for (const ch of card.id) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return FALLBACK_COLORS[hash % FALLBACK_COLORS.length];
}

/** "Plata ••0931", o "BBVA · Azul ••9876" cuando el nombre no incluye al banco. */
export function cardLabel(card: CardIdentity): string {
  const name = card.name.toLowerCase().includes(card.bank.toLowerCase()) ? card.name : `${card.bank} · ${card.name}`;
  return `${name} ••${card.last4}`;
}
