package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.data.MonthSpend
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.card
import mx.finanzas.pareja.ui.cards.CardsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository().apply {
        cardList = listOf(
            card("activa"),
            card("vieja").copy(archived = true),
        )
    }

    private fun vm() = CardsViewModel(repo, userId = "u1", householdId = "h1")

    @Test
    fun `separa las tarjetas activas de las archivadas`() {
        val s = vm().state.value
        assertEquals(listOf("activa"), s.cards.map { it.id })
        assertEquals(listOf("vieja"), s.archived.map { it.id })
    }

    @Test
    fun `archivar mueve la tarjeta a archivadas sin perderla`() {
        val vm = vm()
        vm.archive("activa")
        val s = vm.state.value
        assertEquals(emptyList<String>(), s.cards.map { it.id })
        assertEquals(setOf("activa", "vieja"), s.archived.map { it.id }.toSet())
    }

    @Test
    fun `fijar un presupuesto lo guarda y trae el gasto del mes`() {
        repo.monthSpends["activa"] = MonthSpend(
            monthStart = LocalDate(2026, 10, 1), monthEnd = LocalDate(2026, 10, 31),
            spent = 1700.0, budget = 2000.0, pct = 85.0,
        )
        val vm = vm()
        vm.setBudget("activa", 2000.0)

        assertEquals(listOf("activa" to 2000.0), repo.budgetsSet)
        assertEquals(2000.0, vm.state.value.cards.single().monthlyBudget!!, 0.0)
        assertEquals(1700.0, vm.state.value.spend["activa"]!!.spent, 0.0)
    }

    @Test
    fun `quitar el presupuesto limpia el gasto mostrado`() {
        val vm = vm()
        vm.setBudget("activa", 1500.0)
        vm.setBudget("activa", null)

        assertEquals(null, vm.state.value.cards.single().monthlyBudget)
        assertEquals(emptyMap<String, MonthSpend>(), vm.state.value.spend)
    }

    @Test
    fun `un presupuesto de cero se rechaza sin llamar al servidor`() {
        val vm = vm()
        vm.setBudget("activa", 0.0)

        assertEquals(emptyList<Pair<String, Double?>>(), repo.budgetsSet)
        assertEquals("El presupuesto debe ser mayor a cero", vm.state.value.message)
    }

    @Test
    fun `restaurar devuelve la tarjeta archivada a las activas`() {
        val vm = vm()
        vm.restore("vieja")
        val s = vm.state.value
        assertEquals(setOf("activa", "vieja"), s.cards.map { it.id }.toSet())
        assertEquals(emptyList<String>(), s.archived.map { it.id })
        assertEquals(listOf("vieja"), repo.restoredCards)
    }
}
