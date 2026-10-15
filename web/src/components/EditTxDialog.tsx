"use client";

import { useState, type FormEvent } from "react";
import type { Category, Tx } from "@/lib/data";
import { supabase } from "@/lib/supabase";
import { fieldClass, primaryButton } from "./ui";

interface Props {
  tx: Tx;
  categories: Category[];
  onClose: () => void;
  onSaved: () => void;
}

/** Editar monto, comercio y categoría de un gasto propio. RLS impide tocar los de la pareja. */
export function EditTxDialog({ tx, categories, onClose, onSaved }: Props) {
  const [amount, setAmount] = useState(String(tx.amount));
  const [merchant, setMerchant] = useState(tx.merchant);
  const [categoryId, setCategoryId] = useState(tx.category_id ?? "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save(event: FormEvent) {
    event.preventDefault();
    const value = Number(amount.replace(/[$,\s]/g, ""));
    if (!Number.isFinite(value) || value <= 0) return setError("Escribe un monto mayor a cero");
    if (merchant.trim() === "") return setError("Escribe el comercio");

    setBusy(true);
    setError(null);
    const { data, error: updateError } = await supabase
      .from("transactions")
      .update({ amount: value, merchant: merchant.trim(), category_id: categoryId || null })
      .eq("id", tx.id)
      .select("id");
    setBusy(false);

    if (updateError) return setError("No se pudo guardar. Intenta de nuevo.");
    if (!data || data.length === 0) return setError("Solo puedes editar tus propios gastos.");
    onSaved();
  }

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <form onSubmit={save} onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-3xl bg-card p-6 shadow-card">
        <h2 className="text-2xl font-semibold tracking-[-0.03em]">Editar gasto</h2>
        <div className="mt-4 flex flex-col gap-3">
          <label className="text-sm font-semibold text-muted">Comercio
            <input className={`${fieldClass} mt-1 font-normal text-ink`} value={merchant}
              onChange={(e) => setMerchant(e.target.value)} />
          </label>
          <label className="text-sm font-semibold text-muted">Monto (MXN)
            <input className={`${fieldClass} mt-1 font-normal text-ink`} inputMode="decimal" value={amount}
              onChange={(e) => setAmount(e.target.value)} />
          </label>
          <label className="text-sm font-semibold text-muted">Categoría
            <select className={`${fieldClass} mt-1 font-normal text-ink`} value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}>
              <option value="">Sin categoría</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </label>
        </div>
        {error ? <p className="mt-3 text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        <div className="mt-6 flex justify-end gap-3">
          <button type="button" onClick={onClose} className="px-4 py-3 font-semibold text-muted">Cancelar</button>
          <button className={primaryButton} disabled={busy}>{busy ? "Guardando…" : "Guardar"}</button>
        </div>
      </form>
    </div>
  );
}
