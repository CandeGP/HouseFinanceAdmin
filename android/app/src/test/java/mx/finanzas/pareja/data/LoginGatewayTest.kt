package mx.finanzas.pareja.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LoginGatewayTest {
    @Test
    fun `una sesion valida devuelve los tokens`() {
        val tokens = parseLoginResponse(200, """{"access_token":"a","refresh_token":"r","expires_in":3600}""")
        assertEquals(LoginTokens("a", "r"), tokens)
    }

    @Test
    fun `429 usa los segundos del cuerpo redondeados hacia arriba`() {
        val error = assertThrows(RateLimitedException::class.java) {
            parseLoginResponse(429, """{"error":"rate_limited","retry_after":3.2}""")
        }
        assertEquals(4, error.retryAfterSeconds)
    }

    @Test
    fun `429 sin cuerpo usa Retry-After y si no hay nada 5 segundos`() {
        assertEquals(7, assertThrows(RateLimitedException::class.java) { parseLoginResponse(429, "", "7") }.retryAfterSeconds)
        assertEquals(5, assertThrows(RateLimitedException::class.java) { parseLoginResponse(429, "") }.retryAfterSeconds)
    }

    @Test
    fun `401 se muestra como credenciales incorrectas`() {
        val error = assertThrows(IllegalStateException::class.java) { parseLoginResponse(401, """{"error":"invalid_credentials"}""") }
        assertEquals("Correo o contraseña incorrectos", userMessage(error))
    }

    @Test
    fun `errores del servidor y 200 sin tokens son no disponibles`() {
        assertThrows(LoginUnavailableException::class.java) { parseLoginResponse(503, "") }
        assertThrows(LoginUnavailableException::class.java) { parseLoginResponse(200, "{}") }
    }

    @Test
    fun `el mensaje de limite indica cuantos segundos esperar`() {
        assertEquals("Demasiados intentos. Vuelve a intentar en 4 s.", userMessage(RateLimitedException(4)))
    }
}
