import type { ReactNode } from "react";

/** Superficie base: tarjeta blanca de 12px con sombra teñida de navy, sin borde. */
export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-xl bg-card p-6 shadow-card ${className}`}>{children}</div>;
}

/** Etiqueta en píldora: categorías, modo de pago, estado. */
export function Tag({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex items-center rounded-full bg-canvas px-2 py-1.5 text-[10px] font-semibold uppercase leading-none tracking-[0.04em] text-smoke">
      {children}
    </span>
  );
}

export function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card>
      <p className="text-[10px] font-semibold uppercase tracking-[0.04em] text-smoke">{label}</p>
      <p className="mt-2 text-3xl font-semibold tracking-[-0.03em]">{value}</p>
      {hint ? <p className="mt-1 text-sm text-muted">{hint}</p> : null}
    </Card>
  );
}

/** Barra de uso: porcentaje 0–100 (se recorta). */
export function Bar({ pct }: { pct: number }) {
  const width = Math.max(0, Math.min(100, pct));
  return (
    <div className="h-2 w-full overflow-hidden rounded-full bg-mist">
      <div className="h-full rounded-full bg-steel" style={{ width: `${width}%` }} />
    </div>
  );
}

export function PageTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <header className="mb-8">
      <h1 className="text-[clamp(40px,7vw,100px)] font-bold leading-[0.91] tracking-[-0.125em]">{title}</h1>
      {subtitle ? <p className="mt-4 max-w-xl text-lg text-muted">{subtitle}</p> : null}
    </header>
  );
}

export const primaryButton =
  "rounded-full bg-ink px-4 py-3 text-base font-semibold text-canvas shadow-btn disabled:opacity-50";
export const fieldClass =
  "w-full rounded-xl border border-line bg-card px-4 py-3 text-base outline-none focus:border-accent";
