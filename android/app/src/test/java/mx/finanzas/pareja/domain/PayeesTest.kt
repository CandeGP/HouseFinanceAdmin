package mx.finanzas.pareja.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PayeesTest {
    @Test
    fun `quita vacios y repetidos sin importar mayusculas y deja la escritura mas reciente`() {
        val result = distinctPayees(listOf("Don Pedro", " ", "don pedro", "Ana ", "", "ANA"))
        assertEquals(listOf("Ana", "Don Pedro"), result)
    }

    @Test
    fun `ordena alfabeticamente ignorando mayusculas`() {
        assertEquals(listOf("alma", "Beto", "Zoe"), distinctPayees(listOf("Zoe", "alma", "Beto")))
    }

    @Test
    fun `sin pagos previos no hay sugerencias`() {
        assertEquals(emptyList<String>(), distinctPayees(emptyList()))
    }

    @Test
    fun `filtra sugerencias por lo escrito`() {
        val all = listOf("Ana", "Beto", "Don Pedro")
        assertEquals(listOf("Don Pedro"), matchingPayees(all, " pedro"))
        assertEquals(all, matchingPayees(all, "  "))
        assertEquals(emptyList<String>(), matchingPayees(all, "xyz"))
    }
}
