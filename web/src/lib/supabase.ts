import { createClient } from "@supabase/supabase-js";

const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";

/** false cuando falta .env.local: la app muestra instrucciones en vez de fallar en silencio. */
export const configured = Boolean(url && key);

export const supabase = createClient(url || "http://127.0.0.1:54321", key || "missing");
