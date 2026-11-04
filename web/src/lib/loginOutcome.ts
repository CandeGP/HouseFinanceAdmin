/** Respuesta de la función `login` ya interpretada para mostrarla en pantalla. */
export type LoginOutcome =
  | { kind: "ok"; accessToken: string; refreshToken: string }
  | { kind: "invalid" }
  | { kind: "rate_limited"; retryAfterSeconds: number }
  | { kind: "unavailable" };

const DEFAULT_RETRY_SECONDS = 5;

/** Interpreta estado y cuerpo de la función `login` (separado del fetch para poder probarlo). */
export function parseLoginResponse(status: number, body: unknown, retryAfterHeader?: string | null): LoginOutcome {
  const data = (body ?? {}) as Record<string, unknown>;
  if (status === 200 && typeof data.access_token === "string" && typeof data.refresh_token === "string") {
    return { kind: "ok", accessToken: data.access_token, refreshToken: data.refresh_token };
  }
  if (status === 401) return { kind: "invalid" };
  if (status === 429) {
    const candidates = [Number(data.retry_after), Number(retryAfterHeader)];
    const seconds = candidates.find((value) => Number.isFinite(value) && value > 0) ?? DEFAULT_RETRY_SECONDS;
    return { kind: "rate_limited", retryAfterSeconds: Math.ceil(seconds) };
  }
  return { kind: "unavailable" };
}

export function loginErrorMessage(outcome: LoginOutcome): string | null {
  switch (outcome.kind) {
    case "ok":
      return null;
    case "invalid":
      return "Correo o contraseña incorrectos";
    case "rate_limited":
      return `Demasiados intentos. Vuelve a intentar en ${outcome.retryAfterSeconds} s.`;
    case "unavailable":
      return "No se pudo iniciar sesión. Intenta de nuevo en un momento.";
  }
}
