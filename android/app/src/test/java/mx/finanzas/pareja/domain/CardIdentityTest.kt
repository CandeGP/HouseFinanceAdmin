package mx.finanzas.pareja.domain

import mx.finanzas.pareja.card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CardIdentityTest {
    private fun withBank(id: String, bank: String, name: String = bank, color: String? = null) =
        card(id, name = name).copy(bank = bank, color = color)

    @Test
    fun `usa el color de marca del banco`() {
        assertEquals(0xFF1464A5L, cardAccentArgb(withBank("1", "BBVA")))
        assertEquals(0xFF820AD1L, cardAccentArgb(withBank("2", "Nu")))
        assertEquals(0xFFEC0000L, cardAccentArgb(withBank("3", "Santander")))
    }

    @Test
    fun `el color elegido por la persona gana sobre el del banco`() {
        assertEquals(0xFF112233L, cardAccentArgb(withBank("1", "BBVA", color = "#112233")))
    }

    @Test
    fun `un color invalido se ignora`() {
        assertEquals(0xFF1464A5L, cardAccentArgb(withBank("1", "BBVA", color = "azul")))
    }

    @Test
    fun `banco desconocido recibe un color estable por tarjeta`() {
        val a = withBank("card-a", "Banco Raro")
        assertEquals(cardAccentArgb(a), cardAccentArgb(a.copy()))
        assertNotEquals(0L, cardAccentArgb(a))
    }

    @Test
    fun `etiqueta con banco y ultimos 4`() {
        assertEquals("Plata ••1234", cardLabel(withBank("1", "Plata", name = "Plata")))
        assertEquals("BBVA · Azul ••1234", cardLabel(withBank("2", "BBVA", name = "Azul")))
    }
}
