package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.card
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.domain.DraftField
import mx.finanzas.pareja.tx
import mx.finanzas.pareja.ui.transactions.TransactionFormViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionFormViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val today = LocalDate(2026, 10, 14)
    private val repo = FakeFinanceRepository().apply {
        cardList = listOf(
            card("mine"),
            card("archived-mine").copy(archived = true),
            card("partner").copy(ownerId = "u2"),
        )
        categoryList = listOf(Category("cat-gas", "Gasolina"))
    }

    private fun vm(editingId: String? = null) =
        TransactionFormViewModel(repo, userId = "u1", householdId = "h1", editingId = editingId, today = today)

    @Test
    fun `solo ofrece mis tarjetas activas`() {
        val state = vm().state.value
        assertEquals(listOf("mine"), state.cards.map { it.id })
        assertEquals(1, state.categories.size)
    }

    @Test
    fun `el borrador inicia con la fecha de hoy y contado`() {
        val draft = vm().state.value.draft
        assertEquals(today, draft.date)
        assertEquals(PaymentMode.CONTADO, draft.mode)
    }

    @Test
    fun `guardar un gasto valido lo registra a mi nombre`() {
        val vm = vm()
        vm.onDraftChange(vm.state.value.draft.copy(merchant = "Gasolinera", amountText = "500", cardId = "mine", categoryId = "cat-gas", notes = "viaje"))
        vm.save()

        val added = repo.addedTransactions.single()
        assertEquals("u1", added.ownerId)
        assertEquals("h1", added.householdId)
        assertEquals(500.0, added.amount, 0.0)
        assertEquals("mine", added.cardId)
        assertEquals("viaje", added.notes)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `guardar un gasto invalido no escribe y reporta errores`() {
        val vm = vm()
        vm.save()
        assertTrue(repo.addedTransactions.isEmpty())
        assertNotNull(vm.state.value.errors[DraftField.MERCHANT])
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun `guardar un msi conserva mensualidades y la mensualidad actual`() {
        val vm = vm()
        vm.onDraftChange(
            vm.state.value.draft.copy(
                merchant = "Laptop", amountText = "12000", cardId = "mine",
                mode = PaymentMode.MSI, installmentsText = "12", installmentNumberText = "4",
            ),
        )
        vm.save()
        val added = repo.addedTransactions.single()
        assertEquals(PaymentMode.MSI, added.paymentMode)
        assertEquals(12, added.installments)
        assertEquals(4, added.installmentNumber)
    }

    @Test
    fun `editar carga el gasto existente`() {
        repo.txList += tx("t1", 750.0, cardId = "mine").copy(merchant = "Super", notes = "despensa")
        val draft = vm(editingId = "t1").state.value.draft
        assertEquals("Super", draft.merchant)
        assertEquals("750", draft.amountText)
        assertEquals("despensa", draft.notes)
        assertEquals("mine", draft.cardId)
    }

    @Test
    fun `editar y guardar ajusta el monto con una actualizacion`() {
        repo.txList += tx("t1", 750.0, cardId = "mine")
        val vm = vm(editingId = "t1")
        vm.onDraftChange(vm.state.value.draft.copy(amountText = "800"))
        vm.save()

        val (id, patch) = repo.updatedTransactions.single()
        assertEquals("t1", id)
        assertEquals(800.0, patch.amount, 0.0)
        assertTrue(repo.addedTransactions.isEmpty())
    }

    @Test
    fun `pasar de msi a contado limpia las mensualidades en el parche`() {
        repo.txList += tx("t1", 1200.0, cardId = "mine").copy(
            paymentMode = PaymentMode.MSI, installments = 12, installmentNumber = 3,
        )
        val vm = vm(editingId = "t1")
        vm.onDraftChange(vm.state.value.draft.copy(mode = PaymentMode.CONTADO))
        vm.save()

        val patch = repo.updatedTransactions.single().second
        assertEquals(PaymentMode.CONTADO, patch.paymentMode)
        assertNull(patch.installments)
        assertNull(patch.installmentNumber)
    }

    @Test
    fun `no se puede editar el gasto de otra persona`() {
        repo.txList += tx("t9", 100.0, cardId = "partner", ownerId = "u2")
        val state = vm(editingId = "t9").state.value
        assertEquals("Solo puedes editar tus propios gastos", state.message)
        assertTrue(state.readOnly)
    }

    @Test
    fun `un error del servidor se muestra y no marca guardado`() {
        repo.failWith = Exception("Sin conexión con el servidor")
        val vm = vm()
        vm.onDraftChange(vm.state.value.draft.copy(merchant = "X", amountText = "10", cardId = "mine"))
        vm.save()
        assertFalse(vm.state.value.saved)
        assertNotNull(vm.state.value.message)
    }
}
