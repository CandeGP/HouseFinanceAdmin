package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportsTest {
    private fun slice(label: String, total: Double) = SpendingSlice(label, total, 0.0)

    @Test
    fun `junta ambos meses y ordena por el gasto actual`() {
        val rows = trendRows(
            current = listOf(slice("Comida", 500.0), slice("Gasolina", 200.0)),
            previous = listOf(slice("Gasolina", 400.0), slice("Renta", 900.0)),
        )

        assertEquals(listOf("Comida", "Gasolina", "Renta"), rows.map { it.label })
        assertEquals(0.0, rows[0].previous, 0.0)
        assertEquals(0.0, rows[2].current, 0.0)
    }

    @Test
    fun `calcula el cambio en porcentaje`() {
        val row = TrendRow("Gasolina", current = 200.0, previous = 400.0)

        assertEquals(-200.0, row.delta, 0.0)
        assertEquals(-50.0, row.deltaPct!!, 0.0)
        assertEquals("-50%", row.changeLabel())
        assertEquals("+25%", TrendRow("x", 500.0, 400.0).changeLabel())
    }

    @Test
    fun `compara con el mismo tramo del mes anterior`() {
        val (now, before) = monthToDateRanges(LocalDate(2026, 10, 16))
        assertEquals(LocalDate(2026, 10, 1)..LocalDate(2026, 10, 16), now)
        assertEquals(LocalDate(2026, 9, 1)..LocalDate(2026, 9, 16), before)
    }

    @Test
    fun `si el mes anterior es mas corto topa en su ultimo dia`() {
        val (_, before) = monthToDateRanges(LocalDate(2026, 3, 31))
        assertEquals(LocalDate(2026, 2, 1)..LocalDate(2026, 2, 28), before)
    }

    @Test
    fun `sin gasto previo es nuevo y sin cambio no dice nada`() {
        assertNull(TrendRow("x", 100.0, 0.0).deltaPct)
        assertEquals("nuevo", TrendRow("x", 100.0, 0.0).changeLabel())
        assertNull(TrendRow("x", 100.0, 100.0).changeLabel())
    }
}
