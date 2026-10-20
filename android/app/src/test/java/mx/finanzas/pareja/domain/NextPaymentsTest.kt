package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.card
import mx.finanzas.pareja.cycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.estimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals("Vence 25 oct (en 9 días)", next.dueLine())
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
        assertEquals("Vencido el 25 oct (hace 5 días)", next.dueLine())
    }

    @Test
    fun `con el corte pagado toca el ciclo abierto y lo dice`() {
        val next = nextPayment(view, cycleStatus(CardPayStatus.PAID, target = 1000.0, paid = 1000.0), today)

        assertEquals(2278.48, next.amount, 0.0)
        assertEquals(LocalDate(2026, 11, 5), next.dueDate)
        assertFalse(next.fromClosedCycle)
        assertEquals("Pagado ✓ · siguiente pago vence 5 nov (en 20 días)", next.dueLine())
    }

    @Test
    fun `sin registro de pagos no se inventa una deuda del corte anterior`() {
        val next = nextPayment(view, cycleStatus(CardPayStatus.UNTRACKED, target = 1408.0), today)
        assertEquals(2278.48, next.amount, 0.0)
        assertEquals("Siguiente pago vence 5 nov (en 20 días)", next.dueLine())
    }

    @Test
    fun `los dias se dicen como una persona`() {
        assertEquals("hoy", relativeDays(0))
        assertEquals("mañana", relativeDays(1))
        assertEquals("ayer", relativeDays(-1))
        assertEquals("en 7 días", relativeDays(7))
        assertEquals("hace 3 días", relativeDays(-3))
    }

    private fun rank(id: String) = CardRank(CardCycleView(card(id), estimate(cycleTotal = 100.0)), rank = 1, sharePct = 0.0)

    private fun due(amount: Double, day: Int, month: Int = 10) =
        NextPayment(amount, LocalDate(2026, month, day), fromClosedCycle = true, overdue = false, paidClosedCycle = false)

    @Test
    fun `las tarjetas se ordenan por la fecha limite mas cercana`() {
        val order = sortByDue(
            listOf(rank("lejos"), rank("vencida"), rank("pronto")),
            mapOf("lejos" to due(500.0, 5, 11), "vencida" to due(80.0, 10), "pronto" to due(300.0, 21)),
        ).map { it.view.card.id }

        assertEquals(listOf("vencida", "pronto", "lejos"), order)
    }

    @Test
    fun `las que no deben nada van al final aunque venzan antes`() {
        val order = sortByDue(
            listOf(rank("cero"), rank("debe")),
            mapOf("cero" to due(0.0, 17), "debe" to due(200.0, 28)),
        ).map { it.view.card.id }

        assertEquals(listOf("debe", "cero"), order)
    }

    @Test
    fun `con la misma fecha va primero la que mas debe`() {
        val order = sortByDue(
            listOf(rank("poco"), rank("mucho")),
            mapOf("poco" to due(50.0, 26), "mucho" to due(900.0, 26)),
        ).map { it.view.card.id }

        assertEquals(listOf("mucho", "poco"), order)
    }

    private fun pay(amount: Double, due: LocalDate) =
        NextPayment(amount, due, fromClosedCycle = true, overdue = false, paidClosedCycle = false)

    private val early = LocalDate(2026, 10, 10)

    @Test
    fun `del 1 al 15 separa las quincenas por la fecha de pago y el 15 es de la primera`() {
        val totals = quincenaTotals(
            listOf(pay(100.0, LocalDate(2026, 10, 13)), pay(50.0, LocalDate(2026, 10, 15)), pay(300.0, LocalDate(2026, 10, 16)), pay(25.0, LocalDate(2026, 10, 31))),
            early,
        )
        assertEquals(Quincena(150.0, 2), totals.first.copy(payments = emptyList()))
        assertEquals(Quincena(325.0, 2), totals.second.copy(payments = emptyList()))
        assertNull(totals.nextFirstEnd)
    }

    @Test
    fun `del 1 al 15 los pagos de otros meses y los de cero no cuentan`() {
        val totals = quincenaTotals(listOf(pay(900.0, LocalDate(2026, 11, 5)), pay(0.0, LocalDate(2026, 10, 20))), early)
        assertEquals(NO_QUINCENAS, totals)
    }

    @Test
    fun `pasado el 15 el primer bloque es la primera quincena del mes que viene`() {
        val totals = quincenaTotals(
            listOf(
                pay(700.0, LocalDate(2026, 11, 12)), pay(300.0, LocalDate(2026, 11, 15)), // 1ª de noviembre
                pay(900.0, LocalDate(2026, 11, 26)), // 2ª de noviembre: no cuenta todavía
                pay(400.0, LocalDate(2026, 10, 26)), // sigue por pagar este mes
            ),
            today,
        )
        assertEquals(LocalDate(2026, 11, 15), totals.nextFirstEnd)
        assertEquals(Quincena(1000.0, 2), totals.first.copy(payments = emptyList()))
        assertEquals(Quincena(400.0, 1), totals.second.copy(payments = emptyList()))
    }

    @Test
    fun `pasado el 15 lo vencido y sin pagar sigue visible en el segundo bloque`() {
        val vencido = NextPayment(200.0, LocalDate(2026, 10, 13), fromClosedCycle = true, overdue = true, paidClosedCycle = false)
        val totals = quincenaTotals(listOf(vencido, pay(50.0, LocalDate(2026, 10, 25))), today)

        assertEquals(Quincena(250.0, 2, overdue = 200.0), totals.second.copy(payments = emptyList()))
        assertEquals(Quincena(0.0, 0), totals.first.copy(payments = emptyList()))
    }

    @Test
    fun `en diciembre el mes que viene es enero del otro año`() {
        val totals = quincenaTotals(listOf(pay(120.0, LocalDate(2027, 1, 10))), LocalDate(2026, 12, 20))
        assertEquals(LocalDate(2027, 1, 15), totals.nextFirstEnd)
        assertEquals(Quincena(120.0, 1), totals.first.copy(payments = emptyList()))
    }

    @Test
    fun `las obligaciones juntan el corte sin pagar y el ciclo abierto`() {
        val list = paymentObligations(view, cycleStatus(CardPayStatus.PENDING, target = 2010.97), today)

        assertEquals(listOf(2010.97, 2278.48), list.map { it.amount })
        assertEquals(list.first(), nextPayment(view, cycleStatus(CardPayStatus.PENDING, target = 2010.97), today))
    }
}
