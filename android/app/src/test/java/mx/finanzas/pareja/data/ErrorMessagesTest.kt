package mx.finanzas.pareja.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {
    private val restError = Exception(
        """insert or update on table "members" violates foreign key constraint "members_user_id_fkey" (Key (user_id)=(abc) is not present in table "users".)
URL: http://127.0.0.1:54321/rest/v1/rpc/create_household
Headers: [Authorization=[Bearer eyJhbGciOiJFUzI1NiIsImtpZCI6Ijk.secreto.firma], apikey=[sb_publishable_xxx]]
Http Method: POST""",
    )

    @Test
    fun `nunca expone cabeceras, token ni url`() {
        val message = userMessage(restError)
        assertFalse(message.contains("Bearer"))
        assertFalse(message.contains("eyJ"))
        assertFalse(message.contains("Headers"))
        assertFalse(message.contains("URL:"))
        assertFalse(message.contains("apikey"))
    }

    @Test
    fun `una sesion de un usuario inexistente pide volver a entrar`() {
        assertEquals("Tu sesión ya no es válida. Cierra sesión y vuelve a entrar.", userMessage(restError))
    }

    @Test
    fun `credenciales incorrectas`() {
        assertEquals("Correo o contraseña incorrectos", userMessage(Exception("Invalid login credentials")))
    }

    @Test
    fun `errores de red`() {
        assertEquals("Sin conexión con el servidor", userMessage(IOException("boom")))
    }

    @Test
    fun `mensajes largos o desconocidos se recortan`() {
        val long = "x".repeat(500)
        assertTrue(userMessage(Exception(long)).length <= 200)
    }

    @Test
    fun `sin mensaje usa uno generico`() {
        assertEquals("Ocurrió un error inesperado", userMessage(Exception()))
    }
}
