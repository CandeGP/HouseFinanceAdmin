package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.card
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.TxStatus
import mx.finanzas.pareja.tx
import org.junit.Assert.assertEquals
import org.junit.Test

class SummariesTest {
    private val categories = listOf(Category("cat-gas", "Gasolina"), Category("cat-food", "Comida"))

    @Test
    fun `agrupa por categoria y ordena de mayor a menor`() {
        val txs = listOf(
            tx("1", 100.0, categoryId = "cat-gas"),
            tx("2", 400.0, categoryId = "cat-food"),
            tx("3", 200.0, categoryId = "cat-gas"),
        )
        val result = spendingByCategory(txs, categories)
        assertEquals(listOf("Comida", "Gasolina"), result.map { it.label })
        assertEquals(listOf(400.0, 300.0), result.map { it.total })
        assertEquals(57.14, result[0].pct, 0.01)
    }

    @Test
    fun `gastos sin categoria van a Sin categoria`() {
        val result = spendingByCategory(listOf(tx("1", 100.0, categoryId = null)), categories)
        assertEquals("Sin categoría", result.single().label)
    }

    @Test
    fun `no cuenta gastos pendientes de revision`() {
        val txs = listOf(
            tx("1", 100.0, categoryId = "cat-gas"),
            tx("2", 999.0, categoryId = "cat-gas", status = TxStatus.PENDING_REVIEW),
        )
        assertEquals(100.0, spendingByCategory(txs, categories).single().total, 0.0)
    }

    @Test
    fun `agrupa por tarjeta con su nombre`() {
        val cards = listOf(card("c1", "Nu"), card("c2", "BBVA"))
        val txs = listOf(tx("1", 100.0, cardId = "c1"), tx("2", 400.0, cardId = "c2"), tx("3", 50.0, cardId = null))
        val result = spendingByCard(txs, cards)
        assertEquals(listOf("BBVA", "Nu", "Sin tarjeta"), result.map { it.label })
    }

    @Test
    fun `sin gastos devuelve lista vacia`() {
        assertEquals(emptyList<SpendingSlice>(), spendingByCategory(emptyList(), categories))
    }

    @Test
    fun `limites de un mes normal`() {
        val (from, to) = monthBounds(2026, 10)
        assertEquals(LocalDate(2026, 10, 1), from)
        assertEquals(LocalDate(2026, 10, 31), to)
    }

    @Test
    fun `febrero bisiesto termina el 29`() {
        assertEquals(LocalDate(2028, 2, 29), monthBounds(2028, 2).second)
    }

    @Test
    fun `diciembre termina el 31`() {
        assertEquals(LocalDate(2026, 12, 31), monthBounds(2026, 12).second)
    }
}
