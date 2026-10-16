package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.card
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.domain.ReceiptGuess
import mx.finanzas.pareja.ui.transactions.TransactionFormViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionFormReceiptTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository().apply {
        userId = "u1"
        cardList = listOf(
            card("rappi").copy(last4 = "8476"),
            card("bbva").copy(last4 = "1111", altLast4 = listOf("2222")),
        )
    }

    private fun vm() = TransactionFormViewModel(repo, "u1", "h1", editingId = null, today = LocalDate(2026, 10, 15))

    @Test
    fun `autorrellena comercio, monto y tarjeta por ultimos 4`() {
        val vm = vm()
        vm.applyReceipt(ReceiptGuess(amount = 219.08, merchant = "ENERGETICOS ALTIPLANO", cardLast4 = "8476"))

        val d = vm.state.value.draft
        assertEquals("ENERGETICOS ALTIPLANO", d.merchant)
        assertEquals("219.08", d.amountText)
        assertEquals("rappi", d.cardId)
        assertEquals(PaymentMode.CONTADO, d.mode)
    }

    @Test
    fun `una compra a meses cambia la forma de pago y pone las mensualidades`() {
        val vm = vm()
        vm.applyReceipt(ReceiptGuess(amount = 5400.0, merchant = "LIVERPOOL", installments = 6))

        val d = vm.state.value.draft
        assertEquals(PaymentMode.MSI, d.mode)
        assertEquals("6", d.installmentsText)
        assertEquals("5400.00", d.amountText)
    }

    @Test
    fun `reconoce la tarjeta tambien por un ultimo 4 alterno`() {
        val vm = vm()
        vm.applyReceipt(ReceiptGuess(cardLast4 = "2222"))
        assertEquals("bbva", vm.state.value.draft.cardId)
    }

    @Test
    fun `una tarjeta que no tienes registrada deja la tarjeta vacia y lo avisa`() {
        val vm = vm()
        vm.applyReceipt(ReceiptGuess(amount = 100.0, cardLast4 = "9999"))

        assertNull(vm.state.value.draft.cardId)
        assertTrue(vm.state.value.receiptNote!!.contains("9999"))
    }

    @Test
    fun `lo que no se detecta conserva lo que ya estaba escrito`() {
        val vm = vm()
        vm.onDraftChange(vm.state.value.draft.copy(merchant = "Mi comercio", notes = "nota"))
        vm.applyReceipt(ReceiptGuess(amount = 50.0))

        val d = vm.state.value.draft
        assertEquals("Mi comercio", d.merchant)
        assertEquals("nota", d.notes)
        assertEquals("50.00", d.amountText)
    }

    @Test
    fun `una imagen sin datos lo dice`() {
        val vm = vm()
        vm.applyReceipt(ReceiptGuess())
        assertTrue(vm.state.value.receiptNote!!.startsWith("No pude leer"))
    }

    @Test
    fun `la fecha detectada reemplaza la de hoy`() {
        val vm = vm()
        vm.applyReceipt(ReceiptGuess(date = LocalDate(2026, 10, 3)))
        assertEquals(LocalDate(2026, 10, 3), vm.state.value.draft.date)
    }
}
