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

/** Texto corto del estado de pago de un ciclo (misma redacción que la app). */
export const payLabel = (s: {
  status: string;
  remaining: number;
  target_amount: number;
  due_date: string;
  cutoff_date: string;
}): string => {
  // Siempre nombra su corte: el ciclo en curso es otro y se confundiría con este.
  const cut = `Corte del ${dayLabel(s.cutoff_date)}`;
  switch (s.status) {
    case "paid":
      return s.target_amount <= 0 ? `${cut}: sin pago` : `${cut}: pagado ✓`;
    case "minimum_only":
      return `${cut}: mínimo cubierto · faltan ${mxn(s.remaining)} para no generar intereses`;
    case "partial":
      return `${cut}: pago parcial · faltan ${mxn(s.remaining)}`;
    case "pending":
      return `${cut}: pago pendiente ${mxn(s.remaining)} · vence ${dayLabel(s.due_date)}`;
    case "overdue":
      return `${cut}: vencido · faltan ${mxn(s.remaining)} (venció el ${dayLabel(s.due_date)})`;
    default:
      return `${cut} (${mxn(s.target_amount)}): sin registro de pago`;
  }
};

/** Hoy en la zona del navegador, como "2026-10-16". */
export const todayIso = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
};

export const currentMonth = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
};
