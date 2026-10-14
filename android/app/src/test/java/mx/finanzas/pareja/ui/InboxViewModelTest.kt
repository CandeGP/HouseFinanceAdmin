package mx.finanzas.pareja.ui

import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.card
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.TxStatus
import mx.finanzas.pareja.tx
import mx.finanzas.pareja.ui.inbox.InboxViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InboxViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository().apply {
        txList = mutableListOf(
            tx("p1", 127.0, cardId = null, status = TxStatus.PENDING_REVIEW),
            tx("p2", 90.0, cardId = null, status = TxStatus.PENDING_REVIEW),
            tx("ok", 50.0, status = TxStatus.CONFIRMED),
            tx("partner-pending", 10.0, cardId = null, status = TxStatus.PENDING_REVIEW, ownerId = "u2"),
        )
        cardList = listOf(card("mine"), card("partner").copy(ownerId = "u2"), card("old").copy(archived = true))
        categoryList = listOf(Category("cat-food", "Comida"))
    }

    private fun vm() = InboxViewModel(repo, userId = "u1")

    @Test
    fun `solo lista mis cobros pendientes`() {
        assertEquals(setOf("p1", "p2"), vm().state.value.items.map { it.id }.toSet())
    }

    @Test
    fun `ofrece solo mis tarjetas activas`() {
        assertEquals(listOf("mine"), vm().state.value.cards.map { it.id })
    }

    @Test
    fun `confirmar asigna tarjeta y categoria`() {
        val vm = vm()
        vm.confirm("p1", cardId = "mine", categoryId = "cat-food")
        assertEquals(Triple("p1", "mine", "cat-food"), repo.confirmedTransactions.single())
    }

    @Test
    fun `confirmar sin tarjeta no escribe y avisa`() {
        val vm = vm()
        vm.confirm("p1", cardId = null, categoryId = null)
        assertTrue(repo.confirmedTransactions.isEmpty())
        assertNotNull(vm.state.value.message)
    }

    @Test
    fun `descartar elimina el cobro`() {
        val vm = vm()
        vm.discard("p2")
        assertEquals(listOf("p2"), repo.deletedTransactions)
    }

    @Test
    fun `no descarta cobros de otra persona`() {
        val vm = vm()
        vm.discard("partner-pending")
        assertTrue(repo.deletedTransactions.isEmpty())
    }
}
