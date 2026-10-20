package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.card
import mx.finanzas.pareja.data.MsiInstallment
import mx.finanzas.pareja.ui.dashboard.Scope
import mx.finanzas.pareja.ui.installments.InstallmentsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class InstallmentsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val today = LocalDate(2026, 10, 16)

    private fun inst(card: String, merchant: String, due: LocalDate, n: Int, total: Int, amount: Double) = MsiInstallment(
        cardId = card, transactionId = "t-$merchant-$n", merchant = merchant,
        cycleEnd = due, dueDate = due, number = n, installments = total, amount = amount,
    )

    private val repo = FakeFinanceRepository().apply {
        cardList = listOf(card("mia"), card("suya").copy(ownerId = "u2"))
        msiInstallments = listOf(
            inst("mia", "AOC", LocalDate(2026, 11, 9), 5, 9, 948.61),
            inst("suya", "Converse", LocalDate(2026, 11, 26), 1, 6, 416.50),
            inst("mia", "AOC", LocalDate(2026, 12, 9), 6, 9, 948.61),
        )
    }

    private fun rawVm() = InstallmentsViewModel(repo, userId = "u1", today = today)

    private fun vm() = rawVm().also { it.setScope(Scope.BOTH) }

    @Test
    fun `arranca en Mios y con Ambos agrupa las compras a meses de los dos por mes`() {
        assertEquals(Scope.MINE, rawVm().state.value.scope)
        val s = vm().state.value

        assertEquals(listOf(11, 12), s.months.map { it.month })
        assertEquals(948.61 + 416.50, s.months.first().total, 0.001)
        assertEquals(false, s.loading)
    }

    @Test
    fun `en la vista de mios solo cuentan las tarjetas propias`() {
        val vm = vm()
        vm.setScope(Scope.MINE)

        val s = vm.state.value
        assertEquals(listOf(948.61, 948.61), s.months.map { it.total })
        assertEquals(setOf("mia"), s.months.flatMap { it.items }.map { it.cardId }.toSet())
    }

    @Test
    fun `guarda las tarjetas para poder nombrarlas en la lista`() {
        assertNotNull(vm().state.value.cards["suya"])
    }

    @Test
    fun `si falla la consulta avisa y no deja la pantalla cargando`() {
        repo.msiScheduleFailWith = RuntimeException("sin conexión")
        val s = vm().state.value

        assertEquals(false, s.loading)
        assertNotNull(s.message)
        assertEquals(emptyList<Any>(), s.months)
    }

    @Test
    fun `sin compras a meses la lista queda vacia sin error`() {
        repo.msiInstallments = emptyList()
        val s = vm().state.value

        assertEquals(emptyList<Any>(), s.months)
        assertNull(s.message)
    }
}
