package mx.finanzas.pareja.domain

import mx.finanzas.pareja.tx
import org.junit.Assert.assertEquals
import org.junit.Test

class FormSuggestionsTest {
    @Test
    fun `los mas usados van primero y los nulos no cuentan`() {
        val used = listOf("a", null, "b", "b", "c", "b", "a", null)
        assertEquals(listOf("b", "a", "c"), mostUsedIds(used))
    }

    @Test
    fun `en un empate gana el que aparecio primero`() {
        assertEquals(listOf("x", "y"), mostUsedIds(listOf("x", "y", "y", "x"), limit = 2))
    }

    @Test
    fun `sin historial salen super comida y gasolina`() {
        val all = listOf("g" to "fuel", "o" to "tag", "s" to "cart", "c" to "utensils")
        assertEquals(listOf("s", "c", "g"), suggestedCategoryIds(emptyList(), all))
    }

    @Test
    fun `lo usado manda y se completa con las de arranque`() {
        val all = listOf("g" to "fuel", "o" to "tag", "s" to "cart")
        assertEquals(listOf("o", "s", "g"), suggestedCategoryIds(listOf("o", "o"), all))
    }

    @Test
    fun `un id borrado del historial no se ofrece`() {
        val all = listOf("s" to "cart")
        assertEquals(listOf("s"), suggestedCategoryIds(listOf("ya-no-existe"), all))
    }

    @Test
    fun `sugiere con el gasto mas reciente del mismo comercio sin importar mayusculas`() {
        val history = listOf(
            tx("nuevo", 10.0, cardId = "nu").copy(merchant = "OXXO ", categoryId = "super"),
            tx("viejo", 10.0, cardId = "bbva").copy(merchant = "oxxo", categoryId = "otra"),
        )
        assertEquals("nuevo", suggestForMerchant("  Oxxo", history)?.id)
    }

    @Test
    fun `no sugiere con nombres cortos ni sin coincidencia`() {
        val history = listOf(tx("a", 10.0).copy(merchant = "OX"), tx("b", 10.0).copy(merchant = "Uber"))
        assertEquals(null, suggestForMerchant("OX", history))
        assertEquals(null, suggestForMerchant("Uber Eats", history))
    }

    @Test
    fun `una categoria sin clave usa la etiqueta`() {
        assertEquals("🏷️", categoryEmoji(null))
        assertEquals("⛽", categoryEmoji("fuel"))
    }
}
