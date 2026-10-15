const money = new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN" });
const month = new Intl.DateTimeFormat("es-MX", { month: "long", year: "numeric", timeZone: "UTC" });
const day = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", timeZone: "UTC" });

export const mxn = (value: number): string => money.format(value);

/** "2026-10-14" -> "2026-10" */
export const monthKey = (iso: string): string => iso.slice(0, 7);

/** "2026-10" -> "octubre de 2026" */
export const monthLabel = (key: string): string => month.format(new Date(`${key}-01T00:00:00Z`));

/** "2026-10-14" -> "14 oct" */
export const dayLabel = (iso: string): string => day.format(new Date(`${iso}T00:00:00Z`));

export const currentMonth = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
};
