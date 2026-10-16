package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.card
import mx.finanzas.pareja.cycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.estimate
import mx.finanzas.pareja.tx
import mx.finanzas.pareja.ui.dashboard.DashboardViewModel
import mx.finanzas.pareja.ui.dashboard.Scope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository().apply {
        cardList = listOf(
            card("mine-a"),
            card("mine-b"),
            card("partner").copy(ownerId = "u2"),
            card("old").copy(archived = true),
        )
        estimates["mine-a"] = estimate(100.0)
        estimates["mine-b"] = estimate(900.0)
        estimates["partner"] = estimate(500.0)
        estimates["old"] = estimate(7777.0)
    }

    private fun vm() = DashboardViewModel(repo, userId = "u1", today = { LocalDate(2026, 10, 14) })

    @Test
    fun `por defecto muestra ambos y excluye tarjetas archivadas`() {
        val s = vm().state.value
        assertEquals(Scope.BOTH, s.scope)
        assertEquals(listOf("mine-b", "partner", "mine-a"), s.summary.ranking.map { it.view.card.id })
        assertEquals(1500.0, s.summary.totalCycle, 0.0)
    }

    @Test
    fun `carga el estado de pago de las tarjetas visibles`() {
        repo.cycleStatuses["mine-a"] = cycleStatus(CardPayStatus.PAID, paid = 1000.0)
        repo.cycleStatuses["partner"] = cycleStatus(CardPayStatus.PENDING)
        val s = vm().state.value

        assertEquals(CardPayStatus.PAID, s.statuses["mine-a"]!!.status)
        assertEquals(CardPayStatus.PENDING, s.statuses["partner"]!!.status)
        assertEquals(null, s.statuses["mine-b"])
    }

    private fun monthlyCharge(merchant: String, amount: Double, owner: String) =
        listOf(7, 8, 9, 10).map { month ->
            tx("$merchant-$month", amount, date = LocalDate(2026, month, 12), ownerId = owner).copy(merchant = merchant)
        }

    @Test
    fun `detecta los cargos recurrentes y respeta el alcance Mios y Ambos`() {
        repo.txList = (monthlyCharge("Netflix", 219.0, "u1") + monthlyCharge("Telcel", 399.0, "u2")).toMutableList()
        val vm = vm()

        assertEquals(listOf("Netflix", "Telcel"), vm.state.value.recurring.map { it.merchant }.sorted())

        vm.setScope(Scope.MINE)
        assertEquals(listOf("Netflix"), vm.state.value.recurring.map { it.merchant })
    }

    @Test
    fun `sin historial no hay cargos recurrentes`() {
        assertEquals(emptyList<Any>(), vm().state.value.recurring)
    }

    @Test
    fun `el alcance Mios filtra por duenio`() {
        val vm = vm()
        vm.setScope(Scope.MINE)
        val s = vm.state.value
        assertEquals(listOf("mine-b", "mine-a"), s.summary.ranking.map { it.view.card.id })
        assertEquals(1000.0, s.summary.totalCycle, 0.0)
    }

    @Test
    fun `un error al cargar se expone sin romper`() {
        repo.failWith = null
        val failing = object : mx.finanzas.pareja.data.FinanceRepository by repo {
            override suspend fun cards() = throw Exception("Sin conexión con el servidor")
        }
        val s = DashboardViewModel(failing, "u1") { LocalDate(2026, 10, 14) }.state.value
        assertEquals("Sin conexión con el servidor", s.error)
        assertEquals(false, s.loading)
    }
}
