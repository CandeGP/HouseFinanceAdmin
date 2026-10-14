package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.tx
import mx.finanzas.pareja.ui.dashboard.Scope
import mx.finanzas.pareja.ui.transactions.TransactionsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransactionsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository().apply {
        txList = mutableListOf(
            tx("oct-mine", 100.0, ownerId = "u1", date = LocalDate(2026, 10, 3)),
            tx("oct-partner", 200.0, ownerId = "u2", date = LocalDate(2026, 10, 20)),
            tx("sep-mine", 50.0, ownerId = "u1", date = LocalDate(2026, 9, 30)),
        )
    }

    private fun vm() = TransactionsViewModel(repo, userId = "u1", today = LocalDate(2026, 10, 14))

    @Test
    fun `carga el mes actual con ambos por defecto`() {
        val s = vm().state.value
        assertEquals(2026, s.year)
        assertEquals(10, s.month)
        assertEquals(setOf("oct-mine", "oct-partner"), s.items.map { it.id }.toSet())
        assertEquals(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 31), repo.lastRange)
    }

    @Test
    fun `el alcance Mios filtra por duenio`() {
        val vm = vm()
        vm.setScope(Scope.MINE)
        assertEquals(listOf("oct-mine"), vm.state.value.items.map { it.id })
    }

    @Test
    fun `mes anterior cruza al mes previo`() {
        val vm = vm()
        vm.previousMonth()
        assertEquals(9, vm.state.value.month)
        assertEquals(listOf("sep-mine"), vm.state.value.items.map { it.id })
    }

    @Test
    fun `enero retrocede a diciembre del ano anterior`() {
        val vm = TransactionsViewModel(repo, "u1", LocalDate(2026, 1, 10))
        vm.previousMonth()
        assertEquals(2025, vm.state.value.year)
        assertEquals(12, vm.state.value.month)
    }

    @Test
    fun `eliminar un gasto propio lo borra y recarga`() {
        val vm = vm()
        vm.delete("oct-mine")
        assertEquals(listOf("oct-mine"), repo.deletedTransactions)
        assertEquals(listOf("oct-partner"), vm.state.value.items.map { it.id })
    }

    @Test
    fun `no intenta eliminar gastos de otra persona`() {
        val vm = vm()
        vm.delete("oct-partner")
        assertEquals(emptyList<String>(), repo.deletedTransactions)
    }
}
