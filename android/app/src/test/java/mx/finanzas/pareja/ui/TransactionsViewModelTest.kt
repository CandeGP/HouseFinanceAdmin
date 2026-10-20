package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.tx
import mx.finanzas.pareja.domain.TxSort
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

    private fun rawVm() = TransactionsViewModel(repo, userId = "u1", today = LocalDate(2026, 10, 14))

    private fun vm() = rawVm().also { it.setScope(Scope.BOTH) }

    @Test
    fun `arranca en Mios`() = assertEquals(Scope.MINE, rawVm().state.value.scope)

    @Test
    fun `carga el mes actual con ambos por defecto`() {
        val s = vm().state.value
        assertEquals(2026, s.year)
        assertEquals(10, s.month)
        assertEquals(setOf("oct-mine", "oct-partner"), s.items.map { it.id }.toSet())
        assertEquals(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 31), repo.lastRange)
    }

    @Test
    fun `filtra por tarjeta y ordena por monto sin recargar`() {
        val vm = vm()
        val cardId = vm.state.value.items.first().cardId
        vm.setSort(TxSort.HIGHEST)
        val amounts = vm.state.value.items.map { it.amount }
        assertEquals(amounts.sortedDescending(), amounts)
        vm.setCardFilter("no-existe")
        assertEquals(emptyList<String>(), vm.state.value.items.map { it.id })
        vm.setCardFilter(cardId)
        assertEquals(TxSort.HIGHEST, vm.state.value.sort)
    }

    @Test
    fun `el alcance Mios filtra por duenio`() {
        val vm = vm()
        vm.setScope(Scope.MINE)
        assertEquals(listOf("oct-mine"), vm.state.value.items.map { it.id })
    }

    private fun inst(card: String, merchant: String, due: LocalDate, n: Int, total: Int, amount: Double) =
        mx.finanzas.pareja.data.MsiInstallment(
            cardId = card, transactionId = "t-$merchant-$n", merchant = merchant,
            cycleEnd = due, dueDate = due, number = n, installments = total, amount = amount,
        )

    private fun withInstallments() = repo.apply {
        cardList = listOf(mx.finanzas.pareja.card("mia"), mx.finanzas.pareja.card("suya").copy(ownerId = "u2"))
        msiInstallments = listOf(
            inst("mia", "AOC", LocalDate(2026, 11, 9), 5, 9, 948.61),
            inst("suya", "Converse", LocalDate(2026, 11, 26), 1, 6, 416.50),
            inst("mia", "AOC", LocalDate(2026, 12, 9), 6, 9, 948.61),
        )
    }

    @Test
    fun `un mes futuro sin gastos muestra las mensualidades de compras a meses que se pagan ese mes`() {
        withInstallments()
        val vm = vm()
        vm.nextMonth()

        val s = vm.state.value
        assertEquals(11, s.month)
        assertEquals(emptyList<String>(), s.items.map { it.id })
        assertEquals(listOf("AOC", "Converse"), s.installments.map { it.merchant })
    }

    @Test
    fun `cada mes trae solo sus mensualidades`() {
        withInstallments()
        val vm = vm()
        vm.nextMonth()
        vm.nextMonth()

        assertEquals(listOf("AOC"), vm.state.value.installments.map { it.merchant })
        assertEquals(12, vm.state.value.installments.single().dueDate.monthNumber)
    }

    @Test
    fun `el mes actual sin mensualidades no muestra nada extra`() {
        withInstallments()
        assertEquals(emptyList<Any>(), vm().state.value.installments)
    }

    @Test
    fun `en Mios solo cuentan las mensualidades de mis tarjetas`() {
        withInstallments()
        val vm = vm()
        vm.nextMonth()
        vm.setScope(Scope.MINE)

        assertEquals(listOf("AOC"), vm.state.value.installments.map { it.merchant })
    }

    @Test
    fun `si falla el calendario los gastos del mes siguen apareciendo`() {
        withInstallments().msiScheduleFailWith = RuntimeException("sin conexión")
        val s = vm().state.value

        assertEquals(setOf("oct-mine", "oct-partner"), s.items.map { it.id }.toSet())
        assertEquals(emptyList<Any>(), s.installments)
        assertEquals(null, s.error)
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
