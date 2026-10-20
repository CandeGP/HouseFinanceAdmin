package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.tx
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionFilterTest {
    private val items = listOf(
        tx("a", 100.0, cardId = "c1", date = LocalDate(2026, 10, 1)),
        tx("b", 300.0, cardId = "c2", date = LocalDate(2026, 10, 5)),
        tx("c", 200.0, cardId = "c1", date = LocalDate(2026, 10, 9)),
        tx("d", 200.0, cardId = "c1", date = LocalDate(2026, 10, 3)),
    )

    private fun ids(card: String?, sort: TxSort) = filterAndSort(items, card, sort).map { it.id }

    @Test
    fun `por fecha deja primero lo mas reciente`() = assertEquals(listOf("c", "b", "d", "a"), ids(null, TxSort.DATE))

    @Test
    fun `mayor gasto ordena de mayor a menor y desempata por fecha`() =
        assertEquals(listOf("b", "c", "d", "a"), ids(null, TxSort.HIGHEST))

    @Test
    fun `menor gasto ordena de menor a mayor`() = assertEquals(listOf("a", "c", "d", "b"), ids(null, TxSort.LOWEST))

    @Test
    fun `filtra por tarjeta y conserva el orden`() = assertEquals(listOf("c", "d", "a"), ids("c1", TxSort.DATE))
}
