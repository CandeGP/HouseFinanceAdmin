package mx.finanzas.pareja.domain

import mx.finanzas.pareja.card
import mx.finanzas.pareja.estimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardTest {
    private fun view(id: String, total: Double, remaining: Double = 0.0) =
        CardCycleView(card(id), estimate(total, remaining))

    @Test
    fun `ordena las tarjetas por gasto del ciclo descendente y numera el ranking`() {
        val d = buildDashboard(listOf(view("a", 100.0), view("b", 900.0), view("c", 500.0)))
        assertEquals(listOf("b", "c", "a"), d.ranking.map { it.view.card.id })
        assertEquals(listOf(1, 2, 3), d.ranking.map { it.rank })
    }

    @Test
    fun `calcula el porcentaje de participacion de cada tarjeta`() {
        val d = buildDashboard(listOf(view("a", 250.0), view("b", 750.0)))
        assertEquals(75.0, d.ranking[0].sharePct, 0.01)
        assertEquals(25.0, d.ranking[1].sharePct, 0.01)
        assertEquals(100.0, d.ranking.sumOf { it.sharePct }, 0.01)
    }

    @Test
    fun `suma el total del ciclo y el saldo MSI pendiente`() {
        val d = buildDashboard(listOf(view("a", 100.0, 300.0), view("b", 50.0, 200.0)))
        assertEquals(150.0, d.totalCycle, 0.0)
        assertEquals(500.0, d.totalMsiRemaining, 0.0)
    }

    @Test
    fun `la tarjeta con mas gasto es la principal`() {
        val d = buildDashboard(listOf(view("a", 100.0), view("b", 900.0)))
        assertEquals("b", d.topCard?.id)
    }

    @Test
    fun `sin gasto no hay tarjeta principal y los porcentajes son cero`() {
        val d = buildDashboard(listOf(view("a", 0.0), view("b", 0.0)))
        assertNull(d.topCard)
        assertEquals(0.0, d.ranking.sumOf { it.sharePct }, 0.0)
    }

    @Test
    fun `lista vacia produce resumen vacio`() {
        val d = buildDashboard(emptyList())
        assertEquals(0.0, d.totalCycle, 0.0)
        assertNull(d.topCard)
    }
}
