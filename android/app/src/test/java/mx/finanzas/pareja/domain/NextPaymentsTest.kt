package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.card
import mx.finanzas.pareja.cycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.estimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextPaymentsTest {
    private val today = LocalDate(2026, 10, 16)
    private val view = CardCycleView(card("a"), estimate(cycleTotal = 2278.48, msiRemaining = 22438.42))

    @Test
    fun `con el corte anterior sin pagar toca pagar lo que falta de ese corte`() {
        val status = cycleStatus(CardPayStatus.PENDING, target = 2010.97)
        val next = nextPayment(view, status, today)

        assertEquals(2010.97, next.amount, 0.0)
        assertEquals(LocalDate(2026, 10, 25), next.dueDate)
        assertTrue(next.fromClosedCycle)
        assertEquals("Vence 25 oct", next.dueLine())
    }

    @Test
    fun `un pago parcial muestra solo lo que falta`() {
        val next = nextPayment(view, cycleStatus(CardPayStatus.PARTIAL, target = 1000.0, paid = 400.0), today)
        assertEquals(600.0, next.amount, 0.0)
    }

    @Test
    fun `pasada la fecha limite se marca vencido`() {
        val next = nextPayment(view, cycleStatus(CardPayStatus.OVERDUE, target = 500.0), LocalDate(2026, 10, 30))
        assertTrue(next.overdue)
        assertEquals("Vencido el 25 oct", next.dueLine())
    }

    @Test
    fun `con el corte pagado toca el ciclo abierto y lo dice`() {
        val next = nextPayment(view, cycleStatus(CardPayStatus.PAID, target = 1000.0, paid = 1000.0), today)

        assertEquals(2278.48, next.amount, 0.0)
        assertEquals(LocalDate(2026, 11, 5), next.dueDate)
        assertFalse(next.fromClosedCycle)
        assertEquals("Pagado ✓ · siguiente pago vence 5 nov", next.dueLine())
    }

    @Test
    fun `sin registro de pagos no se inventa una deuda del corte anterior`() {
        val next = nextPayment(view, cycleStatus(CardPayStatus.UNTRACKED, target = 1408.0), today)
        assertEquals(2278.48, next.amount, 0.0)
        assertEquals("Siguiente pago vence 5 nov", next.dueLine())
    }

    private fun pay(amount: Double, due: LocalDate) =
        NextPayment(amount, due, fromClosedCycle = true, overdue = false, paidClosedCycle = false)

    @Test
    fun `separa las quincenas por la fecha de pago y el 15 es de la primera`() {
        val totals = quincenaTotals(
            listOf(pay(100.0, LocalDate(2026, 10, 13)), pay(50.0, LocalDate(2026, 10, 15)), pay(300.0, LocalDate(2026, 10, 16)), pay(25.0, LocalDate(2026, 10, 31))),
            today,
        )
        assertEquals(Quincena(150.0, 2), totals.first)
        assertEquals(Quincena(325.0, 2), totals.second)
    }

    @Test
    fun `los pagos de otros meses y los de cero no cuentan`() {
        val totals = quincenaTotals(listOf(pay(900.0, LocalDate(2026, 11, 5)), pay(0.0, LocalDate(2026, 10, 20))), today)
        assertEquals(NO_QUINCENAS, totals)
    }

    @Test
    fun `lo vencido de un mes anterior sigue sumando`() {
        val totals = quincenaTotals(listOf(pay(200.0, LocalDate(2026, 9, 26))), today)
        assertEquals(Quincena(200.0, 1), totals.second)
    }
}
