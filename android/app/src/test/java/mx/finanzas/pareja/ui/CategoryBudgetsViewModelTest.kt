package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.domain.BudgetLevel
import mx.finanzas.pareja.ui.budgets.CategoryBudgetsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoryBudgetsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val today = LocalDate(2026, 10, 16)

    private val repo = FakeFinanceRepository().apply {
        categoryList = listOf(
            Category("cat-food", "Comida", monthlyBudget = 1000.0),
            Category("cat-fuel", "Gasolina"),
        )
        categorySpends["cat-food"] = 850.0
        categorySpends["cat-fuel"] = 120.0
    }

    private fun vm() = CategoryBudgetsViewModel(repo, today)

    @Test
    fun `carga cada categoria con su gasto del mes y su nivel`() {
        val rows = vm().state.value.rows

        assertEquals(listOf("Comida", "Gasolina"), rows.map { it.category.name })
        assertEquals(850.0, rows[0].spent, 0.0)
        assertEquals(BudgetLevel.NEAR, rows[0].level)
        assertEquals(BudgetLevel.NONE, rows[1].level)
        assertEquals(today, repo.lastCategorySpendRef)
    }

    @Test
    fun `fijar un presupuesto lo guarda, cierra el dialogo y recarga`() {
        val vm = vm()
        vm.setBudget("cat-fuel", " 1,500 ")

        assertEquals(listOf("cat-fuel" to 1500.0), repo.budgetCalls)
        assertTrue(vm.state.value.saved)
        assertEquals(1500.0, vm.state.value.rows.first { it.category.id == "cat-fuel" }.budget!!, 0.0)
    }

    @Test
    fun `dejar el monto vacio quita el presupuesto`() {
        val vm = vm()
        vm.setBudget("cat-food", "  ")

        assertEquals(listOf("cat-food" to null), repo.budgetCalls)
        assertEquals(null, vm.state.value.rows.first { it.category.id == "cat-food" }.budget)
    }

    @Test
    fun `un monto invalido no llega al servidor y muestra el error`() {
        val vm = vm()
        listOf("abc", "0", "-5").forEach { vm.setBudget("cat-food", it) }

        assertTrue(repo.budgetCalls.isEmpty())
        assertEquals("Presupuesto inválido (déjalo vacío para quitarlo)", vm.state.value.error)
        assertEquals(false, vm.state.value.saved)
    }

    @Test
    fun `si falla la carga se avisa sin romper`() {
        repo.failWith = Exception("Sin conexión con el servidor")
        val vm = vm()

        assertNotNull(vm.state.value.message)
        assertEquals(false, vm.state.value.loading)
    }
}
