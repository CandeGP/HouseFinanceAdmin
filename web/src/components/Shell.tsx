"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useState, type FormEvent, type ReactNode } from "react";
import type { Session } from "@supabase/supabase-js";
import { loadFinance, type Finance } from "@/lib/data";
import { configured, supabase } from "@/lib/supabase";
import { Card, fieldClass, primaryButton } from "./ui";

export type Scope = "mine" | "both";

interface FinanceContext {
  finance: Finance;
  uid: string;
  scope: Scope;
}

const Ctx = createContext<FinanceContext | null>(null);

export function useFinance(): FinanceContext {
  const value = useContext(Ctx);
  if (!value) throw new Error("useFinance se usa solo dentro de Shell");
  return value;
}

/** Puerta de acceso: sin configuración o sin sesión muestra su pantalla; con sesión carga los datos. */
export function Shell({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null | undefined>(undefined);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session));
    const { data } = supabase.auth.onAuthStateChange((_event, next) => setSession(next));
    return () => data.subscription.unsubscribe();
  }, []);

  if (!configured) return <Setup />;
  if (session === undefined) return null;
  if (!session) return <Login />;
  return <Workspace uid={session.user.id}>{children}</Workspace>;
}

function Centered({ children }: { children: ReactNode }) {
  return <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-6 p-6">{children}</main>;
}

function Setup() {
  return (
    <Centered>
      <Card>
        <h1 className="text-2xl font-semibold tracking-[-0.03em]">Falta configurar Supabase</h1>
        <p className="mt-2 text-muted">
          Copia <code>web/.env.example</code> a <code>web/.env.local</code> y completa la URL y la llave
          pública (<code>npx supabase status</code>).
        </p>
      </Card>
    </Centered>
  );
}

function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const { error: authError } = await supabase.auth.signInWithPassword({ email, password });
    if (authError) setError("Correo o contraseña incorrectos");
    setBusy(false);
  }

  return (
    <Centered>
      <h1 className="text-[44px] font-bold leading-[0.95] tracking-[-0.05em] [word-spacing:0.08em]">Finanzas en pareja</h1>
      <p className="-mt-3 text-lg text-muted">Inicia sesión con tu cuenta de la app.</p>
      <form onSubmit={submit} className="flex flex-col gap-3">
        <input className={fieldClass} type="email" placeholder="Correo" autoComplete="email" value={email}
          onChange={(e) => setEmail(e.target.value)} required />
        <input className={fieldClass} type="password" placeholder="Contraseña" autoComplete="current-password"
          value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        <button className={primaryButton} disabled={busy}>{busy ? "Entrando…" : "Entrar"}</button>
      </form>
    </Centered>
  );
}

const LINKS = [
  { href: "/", label: "Resumen" },
  { href: "/gastos", label: "Gastos" },
  { href: "/tarjetas", label: "Tarjetas" },
];

function Workspace({ uid, children }: { uid: string; children: ReactNode }) {
  const pathname = usePathname();
  const [finance, setFinance] = useState<Finance | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [scope, setScope] = useState<Scope>("both");

  const reload = useCallback(() => {
    setError(null);
    loadFinance().then(setFinance).catch((e: Error) => setError(e.message));
  }, []);
  useEffect(reload, [reload]);

  return (
    <div className="mx-auto max-w-[1200px] px-4 pb-16 sm:px-6">
      <nav className="sticky top-4 z-10 mt-4 flex flex-wrap items-center justify-between gap-3 rounded-3xl bg-canvas px-6 py-4 shadow-nav sm:px-8">
        <div className="flex gap-6">
          {LINKS.map((link) => (
            <Link key={link.href} href={link.href}
              className={`text-base font-semibold ${pathname === link.href ? "text-accent" : "text-ink"}`}>
              {link.label}
            </Link>
          ))}
        </div>
        <div className="flex items-center gap-3">
          <div className="flex rounded-full bg-mist p-1 text-sm font-semibold">
            {(["mine", "both"] as const).map((value) => (
              <button key={value} onClick={() => setScope(value)}
                className={`rounded-full px-3 py-1 ${scope === value ? "bg-card shadow-btn" : "text-muted"}`}>
                {value === "mine" ? "Míos" : "Ambos"}
              </button>
            ))}
          </div>
          <button onClick={() => supabase.auth.signOut()} className="text-sm font-semibold text-muted">Salir</button>
        </div>
      </nav>

      <main className="mt-12">
        {error ? (
          <Card>
            <p className="font-semibold">No se pudieron cargar los datos</p>
            <p className="mt-1 text-muted">{error}</p>
            <button className={`${primaryButton} mt-4`} onClick={reload}>Reintentar</button>
          </Card>
        ) : finance ? (
          <Ctx.Provider value={{ finance, uid, scope }}>{children}</Ctx.Provider>
        ) : (
          <p className="text-muted">Cargando…</p>
        )}
      </main>
    </div>
  );
}
