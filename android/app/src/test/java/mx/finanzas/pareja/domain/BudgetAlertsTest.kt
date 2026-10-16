package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.card
import mx.finanzas.pareja.data.MonthSpend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAlertsTest {
    private val plata = card("c1", name = "Plata").copy(bank = "Plata", last4 = "0931", monthlyBudget = 2000.0)

    private fun spend(spent: Double, budget: Double? = 2000.0, month: Int = 10) = MonthSpend(
        monthStart = LocalDate(2026, month, 1),
        monthEnd = LocalDate(2026, month, 28),
        spent = spent,
        budget = budget,
        pct = budget?.let { spent / it * 100 },
    )

    @Test
    fun `no avisa mientras el gasto no supere el presupuesto`() {
        assertTrue(budgetAlerts(listOf(CardMonthSpend(plata, spend(1999.99)))).isEmpty())
    }

    @Test
    fun `gastar exactamente el presupuesto no es superarlo`() {
        assertTrue(budgetAlerts(listOf(CardMonthSpend(plata, spend(2000.0)))).isEmpty())
    }

    @Test
    fun `avisa cuando se supera y dice cuanto`() {
        val alerts = budgetAlerts(listOf(CardMonthSpend(plata, spend(2350.0))))

        assertEquals(1, alerts.size)
        val a = alerts[0]
        assertEquals(AlertKind.BUDGET, a.kind)
        assertEquals("c1:BUDGET:2026-10", a.key)
        assertTrue(a.title.contains("Plata"))
        assertTrue(a.title.contains("••0931"))
        assertTrue(a.text.contains("\$2,350.00"))
        assertTrue(a.text.contains("\$2,000.00"))
        assertTrue(a.text.contains("118%"))
        assertEquals(2350.0, a.amount!!, 0.0)
    }

    @Test
    fun `sin presupuesto o sin datos no hay aviso`() {
        assertTrue(budgetAlerts(listOf(CardMonthSpend(plata, spend(5000.0, budget = null)))).isEmpty())
        assertTrue(budgetAlerts(listOf(CardMonthSpend(plata, null))).isEmpty())
    }

    @Test
    fun `el aviso se repite cada mes pero no dentro del mismo mes`() {
        val oct = budgetAlerts(listOf(CardMonthSpend(plata, spend(2500.0, month = 10)))).single().key
        val nov = budgetAlerts(listOf(CardMonthSpend(plata, spend(2500.0, month = 11)))).single().key
        assertEquals("c1:BUDGET:2026-10", oct)
        assertEquals("c1:BUDGET:2026-11", nov)
    }

    @Test
    fun `primero la tarjeta que mas se paso`() {
        val bbva = card("c2", name = "BBVA").copy(bank = "BBVA", last4 = "9876", monthlyBudget = 1000.0)
        val alerts = budgetAlerts(
            listOf(
                CardMonthSpend(plata, spend(2100.0)), // 100 de más
                CardMonthSpend(bbva, spend(1900.0, budget = 1000.0)), // 900 de más
            ),
        )
        assertEquals(listOf("c2", "c1"), alerts.map { it.cardId })
    }
}
