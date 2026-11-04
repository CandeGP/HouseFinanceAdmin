import { loginErrorMessage, parseLoginResponse, type LoginOutcome } from "./loginOutcome";
import { supabase, supabaseAnonKey, supabaseUrl } from "./supabase";

/**
 * Entra por la función `login` del servidor, que aplica el rate limit (cubeta por IP y por usuario) antes de
 * revisar la contraseña. Devuelve el mensaje de error, o null si la sesión quedó iniciada.
 */
export async function signInThroughRateLimit(email: string, password: string): Promise<string | null> {
  let outcome: LoginOutcome;
  try {
    const response = await fetch(`${supabaseUrl}/functions/v1/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json", apikey: supabaseAnonKey, Authorization: `Bearer ${supabaseAnonKey}` },
      body: JSON.stringify({ email, password }),
    });
    const body = await response.json().catch(() => null);
    outcome = parseLoginResponse(response.status, body, response.headers.get("Retry-After"));
  } catch {
    outcome = { kind: "unavailable" };
  }
  if (outcome.kind === "ok") {
    const { error } = await supabase.auth.setSession({ access_token: outcome.accessToken, refresh_token: outcome.refreshToken });
    if (error) return loginErrorMessage({ kind: "unavailable" });
    return null;
  }
  return loginErrorMessage(outcome);
}
