package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.card
import mx.finanzas.pareja.estimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleAlertsTest {
    private val today = LocalDate(2026, 10, 24)
    private val plata = card("c1", name = "Plata").copy(bank = "Plata", last4 = "0931")

    private fun cycle(cutoff: LocalDate, due: LocalDate, total: Double = 678.0) =
        estimate(total).copy(cycleStart = cutoff.let { LocalDate(it.year, it.monthNumber, 1) }, cycleEnd = cutoff, dueDate = due)

    @Test
    fun `avisa del corte cuando faltan pocos dias`() {
        val current = cycle(cutoff = LocalDate(2026, 10, 26), due = LocalDate(2026, 11, 14))
        val alerts = upcomingAlerts(listOf(CardCycles(plata, current, previous = null)), today)

        assertEquals(1, alerts.size)
        assertEquals(AlertKind.CUTOFF, alerts[0].kind)
        assertEquals(2, alerts[0].daysLeft)
    }

    @Test
    fun `no avisa si el corte esta lejos`() {
        val current = cycle(cutoff = LocalDate(2026, 10, 30), due = LocalDate(2026, 11, 19))
        assertTrue(upcomingAlerts(listOf(CardCycles(plata, current, null)), today).isEmpty())
    }

    @Test
    fun `avisa el pago del ciclo anterior con su monto`() {
        val current = cycle(cutoff = LocalDate(2026, 11, 4), due = LocalDate(2026, 11, 26), total = 100.0)
        val previous = cycle(cutoff = LocalDate(2026, 10, 4), due = LocalDate(2026, 10, 26), total = 2010.97)
        val alerts = upcomingAlerts(listOf(CardCycles(plata, current, previous)), today)

        assertEquals(1, alerts.size)
        assertEquals(AlertKind.DUE, alerts[0].kind)
        assertEquals(2, alerts[0].daysLeft)
        assertEquals(2010.97, alerts[0].amount!!, 0.0)
    }

    @Test
    fun `un pago vencido ayer ya no se avisa`() {
        val previous = cycle(cutoff = LocalDate(2026, 9, 4), due = LocalDate(2026, 10, 23))
        val current = cycle(cutoff = LocalDate(2026, 11, 4), due = LocalDate(2026, 11, 26))
        assertTrue(upcomingAlerts(listOf(CardCycles(plata, current, previous)), today).isEmpty())
    }

    @Test
    fun `el pago de hoy se avisa como hoy`() {
        val previous = cycle(cutoff = LocalDate(2026, 10, 4), due = today)
        val alerts = upcomingAlerts(listOf(CardCycles(plata, null, previous)), today)

        assertEquals(0, alerts[0].daysLeft)
        assertTrue(alerts[0].title.contains("hoy"))
    }

    @Test
    fun `la misma fecha de pago en ambos ciclos no se duplica`() {
        val same = cycle(cutoff = LocalDate(2026, 10, 4), due = LocalDate(2026, 10, 26))
        val alerts = upcomingAlerts(listOf(CardCycles(plata, same, same)), today)
        assertEquals(1, alerts.count { it.kind == AlertKind.DUE })
    }

    @Test
    fun `ordena por cercania y la clave es estable por tarjeta, tipo y fecha`() {
        val other = card("c2", name = "BBVA").copy(bank = "BBVA", last4 = "9876")
        val soon = cycle(cutoff = LocalDate(2026, 10, 25), due = LocalDate(2026, 11, 14))
        val later = cycle(cutoff = LocalDate(2026, 10, 27), due = LocalDate(2026, 11, 16))
        val alerts = upcomingAlerts(listOf(CardCycles(plata, later, null), CardCycles(other, soon, null)), today)

        assertEquals(listOf("c2", "c1"), alerts.map { it.cardId })
        assertEquals("c2:CUTOFF:2026-10-25", alerts[0].key)
    }

    @Test
    fun `los textos usan hoy, manana y en N dias`() {
        fun titleFor(days: Int): String {
            val date = LocalDate(2026, 10, 24 + days)
            val previous = cycle(cutoff = LocalDate(2026, 10, 4), due = date)
            return upcomingAlerts(listOf(CardCycles(plata, null, previous)), today).first().title
        }
        assertTrue(titleFor(0).endsWith("hoy"))
        assertTrue(titleFor(1).endsWith("mañana"))
        assertTrue(titleFor(3).endsWith("en 3 días"))
    }
}
