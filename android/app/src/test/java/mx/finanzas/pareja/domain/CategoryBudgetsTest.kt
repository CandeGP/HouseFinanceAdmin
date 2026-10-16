package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.card
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.CategorySpend
import mx.finanzas.pareja.data.MonthSpend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryBudgetsTest {
    private val food = Category("cat-food", "Comida")
    private val fuel = Category("cat-fuel", "Gasolina")

    private fun spend(id: String, spent: Double, budget: Double?, month: Int = 10, household: Double = spent) =
        CategorySpend(
            categoryId = id, monthStart = LocalDate(2026, month, 1), monthEnd = LocalDate(2026, month, 28),
            spent = spent, householdSpent = household, budget = budget, pct = budget?.let { spent / it * 100 },
        )

    private val plata = card("c1", name = "Plata").copy(bank = "Plata", last4 = "0931", monthlyBudget = 2000.0)

    private fun cardSpend(spent: Double, month: Int = 10) = MonthSpend(
        monthStart = LocalDate(2026, month, 1), monthEnd = LocalDate(2026, month, 28),
        spent = spent, budget = 2000.0, pct = spent / 2000.0 * 100,
    )

    @Test
    fun `una categoria por debajo del 80 por ciento no avisa`() {
        assertTrue(categoryBudgetAlerts(listOf(CategoryMonthSpend(food, spend("cat-food", 799.99, 1000.0)))).isEmpty())
    }

    @Test
    fun `desde el 80 por ciento avisa que va cerca y cuanto queda`() {
        val alert = categoryBudgetAlerts(listOf(CategoryMonthSpend(food, spend("cat-food", 800.0, 1000.0)))).single()

        assertEquals(AlertKind.BUDGET_NEAR, alert.kind)
        assertEquals("cat:cat-food:BUDGET_NEAR:2026-10", alert.key)
        assertEquals("Vas en 80% del presupuesto de Comida", alert.title)
        assertTrue(alert.text.contains(formatMxn(200.0)))
    }

    @Test
    fun `gastar exactamente el presupuesto sigue siendo aviso preventivo, no de exceso`() {
        val alert = categoryBudgetAlerts(listOf(CategoryMonthSpend(food, spend("cat-food", 1000.0, 1000.0)))).single()
        assertEquals(AlertKind.BUDGET_NEAR, alert.kind)
    }

    @Test
    fun `al pasarse avisa el exceso y ya no el preventivo`() {
        val alerts = categoryBudgetAlerts(listOf(CategoryMonthSpend(food, spend("cat-food", 1250.0, 1000.0))))

        assertEquals(1, alerts.size)
        assertEquals(AlertKind.BUDGET, alerts.single().kind)
        assertEquals("cat:cat-food:BUDGET:2026-10", alerts.single().key)
        assertTrue(alerts.single().text.contains("125%"))
        assertTrue(alerts.single().text.contains(formatMxn(250.0)))
    }

    @Test
    fun `sin presupuesto no hay aviso`() {
        assertTrue(categoryBudgetAlerts(listOf(CategoryMonthSpend(food, spend("cat-food", 9999.0, null)))).isEmpty())
    }

    @Test
    fun `primero las que se pasaron y luego las que van cerca`() {
        val alerts = categoryBudgetAlerts(
            listOf(
                CategoryMonthSpend(food, spend("cat-food", 900.0, 1000.0)), // cerca (90%)
                CategoryMonthSpend(fuel, spend("cat-fuel", 600.0, 500.0)), // se pasó
            ),
        )
        assertEquals(listOf("Gasolina", "Comida"), alerts.map { it.title.substringAfterLast("de ") })
        assertEquals(listOf(AlertKind.BUDGET, AlertKind.BUDGET_NEAR), alerts.map { it.kind })
    }

    @Test
    fun `los avisos se repiten cada mes pero no dentro del mismo mes`() {
        val oct = categoryBudgetAlerts(listOf(CategoryMonthSpend(food, spend("cat-food", 900.0, 1000.0, month = 10)))).single()
        val nov = categoryBudgetAlerts(listOf(CategoryMonthSpend(food, spend("cat-food", 900.0, 1000.0, month = 11)))).single()
        assertEquals("cat:cat-food:BUDGET_NEAR:2026-10", oct.key)
        assertEquals("cat:cat-food:BUDGET_NEAR:2026-11", nov.key)
    }

    @Test
    fun `una tarjeta entre el 80 y el 100 por ciento avisa lo preventivo`() {
        val alert = budgetWarnings(listOf(CardMonthSpend(plata, cardSpend(1700.0)))).single()

        assertEquals(AlertKind.BUDGET_NEAR, alert.kind)
        assertEquals("c1:BUDGET_NEAR:2026-10", alert.key)
        assertTrue(alert.title.contains("85%"))
        assertTrue(alert.title.contains("Plata"))
        assertTrue(alert.text.contains(formatMxn(300.0)))
    }

    @Test
    fun `las filas ordenan primero las que tienen presupuesto y de mayor a menor uso`() {
        val travel = Category("cat-travel", "Viajes") // sin presupuesto
        val health = Category("cat-health", "Salud") // sin presupuesto, con gasto
        val rows = categoryBudgetRows(
            listOf(food, fuel, travel, health),
            listOf(
                spend("cat-food", 500.0, 1000.0), // 50%
                spend("cat-fuel", 450.0, 500.0), // 90%
                spend("cat-travel", 0.0, null),
                spend("cat-health", 300.0, null),
            ),
        )

        assertEquals(listOf("Gasolina", "Comida", "Salud", "Viajes"), rows.map { it.category.name })
    }

    @Test
    fun `el nivel de cada fila sigue las mismas reglas que los avisos`() {
        fun level(spent: Double, budget: Double?) =
            categoryBudgetRows(listOf(food), listOf(spend("cat-food", spent, budget))).single().level

        assertEquals(BudgetLevel.NONE, level(5000.0, null))
        assertEquals(BudgetLevel.OK, level(799.99, 1000.0))
        assertEquals(BudgetLevel.NEAR, level(800.0, 1000.0))
        assertEquals(BudgetLevel.NEAR, level(1000.0, 1000.0))
        assertEquals(BudgetLevel.OVER, level(1000.01, 1000.0))
    }

    @Test
    fun `una categoria sin datos cuenta como cero y sin presupuesto`() {
        val row = categoryBudgetRows(listOf(food), emptyList()).single()
        assertEquals(0.0, row.spent, 0.0)
        assertEquals(null, row.budget)
        assertEquals(BudgetLevel.NONE, row.level)
    }

    @Test
    fun `el gasto y el presupuesto de cada fila salen del gasto de quien consulta`() {
        // Mi pareja gastó 4,500 en Educación: a mí me llega 0 y sin presupuesto, y a ella el suyo.
        val education = Category("cat-edu", "Educación")
        val mine = categoryBudgetRows(
            listOf(education), listOf(spend("cat-edu", 0.0, null, household = 4500.0)),
        ).single()
        val hers = categoryBudgetRows(listOf(education), listOf(spend("cat-edu", 4500.0, 5000.0))).single()

        assertEquals(0.0, mine.spent, 0.0)
        assertEquals(BudgetLevel.NONE, mine.level)
        assertEquals(4500.0, mine.householdSpent, 0.0) // la categoría y el gasto del hogar los ven los dos
        assertEquals(4500.0, hers.spent, 0.0)
        assertEquals(BudgetLevel.NEAR, hers.level)
    }

    @Test
    fun `una tarjeta fuera de ese rango no genera aviso preventivo`() {
        assertTrue(budgetWarnings(listOf(CardMonthSpend(plata, cardSpend(1599.0)))).isEmpty())
        assertTrue(budgetWarnings(listOf(CardMonthSpend(plata, cardSpend(2000.01)))).isEmpty())
        assertTrue(budgetWarnings(listOf(CardMonthSpend(plata, null))).isEmpty())
    }
}
