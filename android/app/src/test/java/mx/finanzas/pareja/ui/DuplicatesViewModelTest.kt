package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.data.TxSource
import mx.finanzas.pareja.tx
import mx.finanzas.pareja.ui.duplicates.DuplicatesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DuplicatesViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val today = LocalDate(2026, 10, 16)

    private val repo = FakeFinanceRepository().apply {
        txList.addAll(
            listOf(
                tx("a", 100.0).copy(merchant = "Liverpool", source = TxSource.NOTIFICATION),
                tx("b", 100.0, ownerId = "u2").copy(merchant = "Liverpool", source = TxSource.STATEMENT),
            ),
        )
    }
    private val saved = mutableListOf<Set<String>>()

    private fun vm(dismissed: Set<String> = emptySet()) =
        DuplicatesViewModel(repo, userId = "u1", today = today, dismissed = dismissed) { saved += it }

    @Test
    fun `lista los pares sospechosos de los ultimos dias`() {
        assertEquals(listOf("a|b"), vm().state.value.pairs.map { it.key })
    }

    @Test
    fun `no es duplicado lo oculta y lo recuerda`() {
        val vm = vm()
        vm.dismiss(vm.state.value.pairs.single())

        assertTrue(vm.state.value.pairs.isEmpty())
        assertEquals(listOf(setOf("a|b")), saved)
        assertTrue(vm(dismissed = setOf("a|b")).state.value.pairs.isEmpty())
    }

    @Test
    fun `borrar un cargo propio lo elimina y quita el par`() {
        val vm = vm()
        vm.delete(vm.state.value.pairs.single(), "a")

        assertEquals(listOf("a"), repo.deletedTransactions)
        assertTrue(vm.state.value.pairs.isEmpty())
    }

    @Test
    fun `no borra cargos de la pareja`() {
        val vm = vm()
        vm.delete(vm.state.value.pairs.single(), "b")

        assertTrue(repo.deletedTransactions.isEmpty())
    }
}
