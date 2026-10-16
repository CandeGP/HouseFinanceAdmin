package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.data.CycleEstimate
import mx.finanzas.pareja.data.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPaymentsTest {
    private fun status(
        status: CardPayStatus, target: Double = 1407.38, paid: Double = 0.0, remaining: Double = target - paid,
    ) = CardCycleStatus(
        cutoffDate = LocalDate(2026, 10, 10), dueDate = LocalDate(2026, 10, 25),
        targetAmount = target, targetSource = "statement", paidAmount = paid, remaining = remaining, status = status,
    )

    @Test
    fun `pendiente dice cuanto falta y cuando vence`() {
        assertEquals(
            "Corte del 10 oct: pago pendiente ${formatMxn(1407.38)} · vence 25 oct",
            status(CardPayStatus.PENDING).payLabel(),
        )
    }

    @Test
    fun `parcial y minimo cubierto dicen cuanto falta`() {
        assertEquals(
            "Corte del 10 oct: pago parcial · faltan ${formatMxn(907.38)}",
            status(CardPayStatus.PARTIAL, paid = 500.0).payLabel(),
        )
        assertEquals(
            "Corte del 10 oct: mínimo cubierto · faltan ${formatMxn(1157.38)} para no generar intereses",
            status(CardPayStatus.MINIMUM_ONLY, paid = 250.0).payLabel(),
        )
    }

    @Test
    fun `pagado distingue ciclo cubierto de ciclo sin saldo`() {
        assertEquals("Corte del 10 oct: pagado ✓", status(CardPayStatus.PAID, paid = 1407.38).payLabel())
        assertEquals("Corte del 10 oct: sin pago", status(CardPayStatus.PAID, target = 0.0).payLabel())
    }

    @Test
    fun `vencido y sin registro mencionan las fechas`() {
        assertEquals(
            "Corte del 10 oct: vencido · faltan ${formatMxn(1407.38)} (venció el 25 oct)",
            status(CardPayStatus.OVERDUE).payLabel(),
        )
        assertEquals(
            "Corte del 10 oct (${formatMxn(1407.38)}): sin registro de pago",
            status(CardPayStatus.UNTRACKED).payLabel(),
        )
    }

    @Test
    fun `solo pagado cuenta como liquidado`() {
        assertTrue(status(CardPayStatus.PAID).isSettled)
        CardPayStatus.entries.filter { it != CardPayStatus.PAID }.forEach {
            assertFalse(it.name, status(it).isSettled)
        }
    }

    @Test
    fun `la llave del aviso de pago coincide con la que genera el aviso`() {
        val card = Card(
            id = "c1", householdId = "h", ownerId = "u", name = "BBVA", bank = "BBVA", last4 = "5436",
            creditLimit = 50000.0, cutoffDay = 10, dueDay = 25,
        )
        val estimate = CycleEstimate(
            cycleStart = LocalDate(2026, 9, 11), cycleEnd = LocalDate(2026, 10, 10), dueDate = LocalDate(2026, 10, 25),
            cycleContadoTotal = 1407.38, cycleMsiTotal = 0.0, cycleTotal = 1407.38, msiRemaining = 0.0,
            creditLimit = 50000.0, usagePct = 3.0,
        )
        val alert = upcomingAlerts(listOf(CardCycles(card, null, estimate)), LocalDate(2026, 10, 22)).single()
        assertEquals(status(CardPayStatus.PAID).dueAlertKey("c1"), alert.key)
    }

    @Test
    fun `registrar pago valida el monto y limpia la nota`() {
        val ok = CardPaymentDraft(amountText = " $1,407.38 ", method = PaymentMethod.CASH, notes = "  en ventanilla ").validate()
        assertEquals(CardPaymentResult.Valid(1407.38, PaymentMethod.CASH, "en ventanilla"), ok)
        assertEquals(CardPaymentResult.Valid(10.0, PaymentMethod.TRANSFER, null), CardPaymentDraft("10").validate())
    }

    @Test
    fun `registrar pago rechaza montos vacios, cero o negativos`() {
        listOf("", "0", "-5", "abc").forEach {
            assertEquals(it, CardPaymentResult.Invalid("Monto inválido"), CardPaymentDraft(amountText = it).validate())
        }
    }
}
