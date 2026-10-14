package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDraftTest {
    private val date = LocalDate(2026, 10, 10)

    private fun draft(
        merchant: String = "Gasolinera",
        amount: String = "500",
        cardId: String? = "c1",
        mode: PaymentMode = PaymentMode.CONTADO,
        installments: String = "",
        installmentNumber: String = "",
        notes: String = "",
    ) = TransactionDraft(
        merchant = merchant, amountText = amount, date = date, cardId = cardId, categoryId = null,
        mode = mode, installmentsText = installments, installmentNumberText = installmentNumber, notes = notes,
    )

    private fun invalid(d: TransactionDraft) = (d.validate() as DraftResult.Invalid).errors
    private fun valid(d: TransactionDraft) = (d.validate() as DraftResult.Valid).value

    // --- parseAmount
    @Test fun `parseAmount acepta decimales simples`() = assertEquals(1234.5, parseAmount("1234.5")!!, 0.0)
    @Test fun `parseAmount acepta signo de pesos y comas`() = assertEquals(1234.5, parseAmount("\$1,234.50")!!, 0.0)
    @Test fun `parseAmount ignora espacios`() = assertEquals(99.0, parseAmount(" 99 ")!!, 0.0)
    @Test fun `parseAmount redondea a dos decimales`() = assertEquals(11.0, parseAmount("10.999")!!, 0.0)
    @Test fun `parseAmount rechaza texto`() = assertNull(parseAmount("abc"))
    @Test fun `parseAmount rechaza vacio`() = assertNull(parseAmount(""))
    @Test fun `parseAmount rechaza negativos`() = assertNull(parseAmount("-5"))
    @Test fun `parseAmount rechaza cero`() = assertNull(parseAmount("0"))

    // --- contado
    @Test
    fun `contado valido recorta comercio e ignora mensualidades escritas`() {
        val v = valid(draft(merchant = "  Gasolinera  ", installments = "6", installmentNumber = "2"))
        assertEquals("Gasolinera", v.merchant)
        assertEquals(500.0, v.amount, 0.0)
        assertNull(v.installments)
        assertNull(v.installmentNumber)
    }

    @Test
    fun `notas en blanco se guardan como null`() {
        assertNull(valid(draft(notes = "   ")).notes)
        assertEquals("despensa", valid(draft(notes = " despensa ")).notes)
    }

    @Test
    fun `comercio vacio es invalido`() {
        assertNotNull(invalid(draft(merchant = "  "))[DraftField.MERCHANT])
    }

    @Test
    fun `monto cero es invalido`() {
        assertNotNull(invalid(draft(amount = "0"))[DraftField.AMOUNT])
    }

    @Test
    fun `tarjeta es obligatoria`() {
        assertNotNull(invalid(draft(cardId = null))[DraftField.CARD])
    }

    @Test
    fun `reporta varios errores a la vez`() {
        val errors = invalid(draft(merchant = "", amount = "x", cardId = null))
        assertTrue(errors.keys.containsAll(listOf(DraftField.MERCHANT, DraftField.AMOUNT, DraftField.CARD)))
    }

    // --- MSI
    @Test
    fun `msi con una sola mensualidad es invalido`() {
        assertNotNull(invalid(draft(mode = PaymentMode.MSI, installments = "1"))[DraftField.INSTALLMENTS])
    }

    @Test
    fun `msi sin mensualidades es invalido`() {
        assertNotNull(invalid(draft(mode = PaymentMode.MSI, installments = ""))[DraftField.INSTALLMENTS])
    }

    @Test
    fun `msi sin numero de mensualidad actual asume la primera`() {
        val v = valid(draft(mode = PaymentMode.MSI, installments = "12"))
        assertEquals(12, v.installments)
        assertEquals(1, v.installmentNumber)
    }

    @Test
    fun `msi respeta la mensualidad en la que va`() {
        val v = valid(draft(mode = PaymentMode.MSI, installments = "12", installmentNumber = "4"))
        assertEquals(4, v.installmentNumber)
    }

    @Test
    fun `msi no permite mensualidad actual mayor al total`() {
        val errors = invalid(draft(mode = PaymentMode.MSI, installments = "6", installmentNumber = "7"))
        assertNotNull(errors[DraftField.INSTALLMENT_NUMBER])
    }

    @Test
    fun `msi no permite mensualidad actual negativa`() {
        val errors = invalid(draft(mode = PaymentMode.MSI, installments = "6", installmentNumber = "-1"))
        assertNotNull(errors[DraftField.INSTALLMENT_NUMBER])
    }

    @Test
    fun `msi permite mensualidad 0 cuando la primera cae en el corte siguiente`() {
        val v = valid(draft(mode = PaymentMode.MSI, installments = "6", installmentNumber = "0"))
        assertEquals(0, v.installmentNumber)
    }
}
