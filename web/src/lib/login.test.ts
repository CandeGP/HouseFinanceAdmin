import assert from "node:assert/strict";
import { test } from "node:test";
import { loginErrorMessage, parseLoginResponse } from "./loginOutcome.ts";

test("una sesión válida devuelve los tokens", () => {
  const outcome = parseLoginResponse(200, { access_token: "a", refresh_token: "r" });
  assert.deepEqual(outcome, { kind: "ok", accessToken: "a", refreshToken: "r" });
});

test("un 200 sin tokens no cuenta como sesión", () => {
  assert.equal(parseLoginResponse(200, {}).kind, "unavailable");
});

test("401 es credenciales inválidas, con un solo mensaje", () => {
  assert.equal(loginErrorMessage(parseLoginResponse(401, { error: "invalid_credentials" })), "Correo o contraseña incorrectos");
});

test("429 usa los segundos del cuerpo y los redondea hacia arriba", () => {
  const outcome = parseLoginResponse(429, { error: "rate_limited", retry_after: 3.2 });
  assert.deepEqual(outcome, { kind: "rate_limited", retryAfterSeconds: 4 });
  assert.equal(loginErrorMessage(outcome), "Demasiados intentos. Vuelve a intentar en 4 s.");
});

test("429 sin cuerpo usa la cabecera Retry-After, y si no hay nada, 5 s", () => {
  assert.deepEqual(parseLoginResponse(429, null, "7"), { kind: "rate_limited", retryAfterSeconds: 7 });
  assert.deepEqual(parseLoginResponse(429, null), { kind: "rate_limited", retryAfterSeconds: 5 });
});

test("errores del servidor se muestran como no disponible", () => {
  for (const status of [500, 502, 503]) assert.equal(parseLoginResponse(status, null).kind, "unavailable");
});
