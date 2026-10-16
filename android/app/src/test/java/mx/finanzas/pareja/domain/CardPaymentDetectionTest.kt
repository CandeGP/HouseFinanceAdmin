package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.CardPayment
import mx.finanzas.pareja.data.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPaymentDetectionTest {
    private fun movement(description: String, amount: Double, on: LocalDate = LocalDate(2026, 9, 9)) =
        StatementMovement(
            operationDate = on, chargeDate = on, description = description, amount = amount,
            cardLast4 = null, isInstallmentCharge = false,
        )

    private fun summary(periodStart: LocalDate?, cutoff: LocalDate) = StatementSummary(
        bank = null, cardLast4 = null, periodStart = periodStart, periodEnd = cutoff, cutoffDate = cutoff,
        dueDate = null, creditLimit = null, payNoInterest = null, minimumPayment = null,
    )

    private fun payment(amount: Double) = CardPayment(
        id = "p$amount", householdId = "h", ownerId = "u", cardId = "c", cutoffDate = LocalDate(2026, 8, 18),
        amount = amount, paidAt = Instant.parse("2026-09-09T18:00:00Z"), method = PaymentMethod.TRANSFER,
    )

    @Test
    fun `reconoce los pagos reales de los estados de cuenta`() {
        listOf("PAGO POR SPEI", "Pago por transferencia SPEI", "BMOVIL.PAGO TDC", "¡Gracias por tu pago! | RFC: S.I.")
            .forEach { assertTrue(it, movement(it, -100.0).isCardPayment()) }
    }

    @Test
    fun `una bonificacion o devolucion no es un pago`() {
        listOf("Abono de \"Amazon A Meses\" | RFC: S.I.", "DEVOLUCION TIENDA", "Reembolso por pago duplicado", "Bonificación")
            .forEach { assertFalse(it, movement(it, -100.0).isCardPayment()) }
    }

    @Test
    fun `un cargo no es un pago aunque diga pago`() {
        assertFalse(movement("MERCADO PAGO", 1665.56).isCardPayment())
    }

    @Test
    fun `el corte que pagan es el anterior al del estado`() {
        assertEquals(
            LocalDate(2026, 8, 18),
            previousCutoff(summary(periodStart = LocalDate(2026, 8, 19), cutoff = LocalDate(2026, 9, 18))),
        )
        assertEquals(
            LocalDate(2026, 8, 18),
            previousCutoff(summary(periodStart = null, cutoff = LocalDate(2026, 9, 18))),
        )
    }

    @Test
    fun `sin periodo, un corte a fin de mes se acomoda al mes anterior`() {
        assertEquals(
            LocalDate(2026, 2, 28),
            previousCutoff(summary(periodStart = null, cutoff = LocalDate(2026, 3, 31))),
        )
    }

    @Test
    fun `un pago ya capturado a mano con el mismo monto no se vuelve a ofrecer`() {
        val detected = detectCardPayments(
            listOf(
                movement("PAGO POR SPEI", -1000.0, LocalDate(2026, 9, 2)),
                movement("PAGO POR SPEI", -1000.0, LocalDate(2026, 9, 9)),
                movement("Abono de \"Amazon A Meses\"", -400.58),
            ),
            existing = listOf(payment(1000.0)),
        )

        assertEquals(2, detected.size)
        assertEquals(listOf(true, false), detected.map { it.alreadyRegistered })
        assertEquals(1000.0, detected[0].amount, 0.0)
    }
}
