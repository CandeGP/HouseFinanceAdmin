package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import mx.finanzas.pareja.data.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ManualPaymentDraftTest {
    private val zone = TimeZone.of("America/Mexico_City")

    private fun draft(
        amount: String = "350",
        time: String = "14:30",
        concept: String = "Mecánico",
        method: PaymentMethod = PaymentMethod.CASH,
        counterparty: String = "",
        reference: String = "",
        notes: String = "",
    ) = ManualPaymentDraft(
        method = method, amountText = amount, date = LocalDate(2026, 10, 12), timeText = time,
        concept = concept, counterparty = counterparty, reference = reference, notes = notes,
    )

    private fun valid(d: ManualPaymentDraft) = (d.validate(zone) as PaymentDraftResult.Valid).value
    private fun invalid(d: ManualPaymentDraft) = (d.validate(zone) as PaymentDraftResult.Invalid).errors

    @Test
    fun `convierte fecha y hora locales a instante UTC`() {
        assertEquals(Instant.parse("2026-10-12T20:30:00Z"), valid(draft()).paidAt)
    }

    @Test
    fun `acepta hora de un digito`() {
        assertEquals(Instant.parse("2026-10-12T15:05:00Z"), valid(draft(time = "9:05")).paidAt)
    }

    @Test fun `hora 25 es invalida`() = assertNotNull(invalid(draft(time = "25:00"))[PaymentField.TIME])
    @Test fun `minutos 60 son invalidos`() = assertNotNull(invalid(draft(time = "14:60"))[PaymentField.TIME])
    @Test fun `hora vacia es invalida`() = assertNotNull(invalid(draft(time = ""))[PaymentField.TIME])
    @Test fun `concepto vacio es invalido`() = assertNotNull(invalid(draft(concept = " "))[PaymentField.CONCEPT])
    @Test fun `monto invalido`() = assertNotNull(invalid(draft(amount = "abc"))[PaymentField.AMOUNT])

    @Test
    fun `campos opcionales en blanco quedan en null y se recortan`() {
        val v = valid(draft(counterparty = "  ", reference = "", notes = "  ", concept = "  Renta  "))
        assertNull(v.counterparty)
        assertNull(v.reference)
        assertNull(v.notes)
        assertEquals("Renta", v.concept)
    }

    @Test
    fun `conserva metodo, referencia y destinatario`() {
        val v = valid(draft(method = PaymentMethod.TRANSFER, counterparty = "Juan", reference = "ABC123"))
        assertEquals(PaymentMethod.TRANSFER, v.method)
        assertEquals("Juan", v.counterparty)
        assertEquals("ABC123", v.reference)
    }

    @Test
    fun `timeText y localDate hacen el recorrido inverso`() {
        val instant = Instant.parse("2026-10-12T20:30:00Z")
        assertEquals("14:30", timeText(instant, zone))
        assertEquals(LocalDate(2026, 10, 12), localDate(instant, zone))
    }
}
