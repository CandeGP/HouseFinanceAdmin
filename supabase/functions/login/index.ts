// Login con rate limit tipo "token bucket" (cubeta de fichas que se rellena de forma continua).
//
//   por IP:      capacidad 30, recarga 30 fichas cada 20 s  = 1.5 fichas por segundo
//   por usuario: capacidad 12, recarga 12 fichas cada 60 s  = 0.2 fichas por segundo
//
// La app Android y la web entran SOLO por aquí: esta función gasta una ficha de cada cubeta y, si ambas
// alcanzan, pide la sesión a Supabase Auth. Si alguna cubeta está vacía responde 429 con `retry_after`
// (segundos hasta que vuelva la siguiente ficha) y nunca llega a revisar la contraseña.
//
// El estado de las cubetas vive en Postgres (`public.take_token`), así que es el mismo para todas las
// instancias de la función y no se reinicia al redesplegar.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const IP_BUCKET = { capacity: 30, refillPerSec: 30 / 20 };
const USER_BUCKET = { capacity: 12, refillPerSec: 12 / 60 };

const MAX_BODY_BYTES = 4 * 1024;
const MAX_EMAIL_LENGTH = 254;
const MAX_PASSWORD_LENGTH = 512;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Max-Age": "86400",
};

function json(status: number, body: Record<string, unknown>, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json", "Cache-Control": "no-store", ...extra },
  });
}

/** IP del cliente. `cf-connecting-ip` lo pone el borde de Supabase y no se puede falsear desde fuera. */
function clientIp(req: Request): string {
  const direct = req.headers.get("cf-connecting-ip") ?? req.headers.get("x-real-ip");
  if (direct) return direct.trim();
  // Último recurso: la última IP de x-forwarded-for es la que vio el primer proxy confiable.
  const forwarded = req.headers.get("x-forwarded-for");
  if (forwarded) return forwarded.split(",").pop()!.trim();
  return "unknown";
}

async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

interface TakeResult {
  allowed: boolean;
  retry_after: number;
}

async function takeToken(key: string, bucket: { capacity: number; refillPerSec: number }): Promise<TakeResult> {
  const { data, error } = await admin.rpc("take_token", {
    p_key: key,
    p_capacity: bucket.capacity,
    p_refill_per_sec: bucket.refillPerSec,
  });
  if (error || !Array.isArray(data) || data.length === 0) throw new Error("rate limit unavailable");
  return { allowed: Boolean(data[0].allowed), retry_after: Number(data[0].retry_after) };
}

function rateLimited(scope: "ip" | "user", retryAfter: number): Response {
  const seconds = Math.max(1, Math.ceil(retryAfter));
  return json(429, { error: "rate_limited", scope, retry_after: seconds }, { "Retry-After": String(seconds) });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const raw = await req.text();
  if (raw.length > MAX_BODY_BYTES) return json(413, { error: "payload_too_large" });

  let email = "";
  let password = "";
  try {
    const body = JSON.parse(raw);
    email = String(body.email ?? "").trim().toLowerCase();
    password = String(body.password ?? "");
  } catch {
    return json(400, { error: "bad_request" });
  }
  if (!EMAIL_PATTERN.test(email) || email.length > MAX_EMAIL_LENGTH || password.length === 0 || password.length > MAX_PASSWORD_LENGTH) {
    return json(400, { error: "bad_request" });
  }

  // 1) Cubeta por IP: frena a quien prueba muchas cuentas o contraseñas desde el mismo lugar.
  // 2) Cubeta por usuario: frena a quien ataca una cuenta desde muchas IPs.
  // Si el límite no se puede consultar, se rechaza (falla cerrado): es preferible a dejar pasar sin freno.
  try {
    const ip = await takeToken(`ip:${clientIp(req)}`, IP_BUCKET);
    if (!ip.allowed) return rateLimited("ip", ip.retry_after);
    const user = await takeToken(`user:${await sha256Hex(email)}`, USER_BUCKET);
    if (!user.allowed) return rateLimited("user", user.retry_after);
  } catch {
    return json(503, { error: "unavailable" }, { "Retry-After": "5" });
  }

  const response = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: { apikey: ANON_KEY, "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });

  if (response.status === 200) {
    const session = await response.json();
    return json(200, session);
  }
  // Mensaje único: no se distingue "correo inexistente" de "contraseña incorrecta".
  if (response.status === 400 || response.status === 401 || response.status === 422) {
    return json(401, { error: "invalid_credentials" });
  }
  if (response.status === 429) return json(429, { error: "rate_limited", scope: "auth", retry_after: 30 }, { "Retry-After": "30" });
  return json(502, { error: "auth_unavailable" });
});
