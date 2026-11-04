package mx.finanzas.pareja.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.domain.PaymentPart
import mx.finanzas.pareja.domain.splitPayment
import org.junit.Assert.assertEquals
import org.junit.Test

class CardCreditsTest {
    private val cycleEnd = LocalDate(2026, 11, 4)

    private fun snapshot(cycleTotal: Double = 3000.0, msiRemaining: Double = 5000.0) = CardSnapshot(
        cardId = "c1",
        estimate = CycleEstimate(
            cycleStart = LocalDate(2026, 10, 5), cycleEnd = cycleEnd, dueDate = LocalDate(2026, 11, 24),
            cycleContadoTotal = 1000.0, cycleMsiTotal = 2000.0, cycleTotal = cycleTotal,
            msiRemaining = msiRemaining, creditLimit = 20000.0, usagePct = 10.0,
        ),
        status = null,
    )

    private fun credit(amount: Double, destination: PaymentDestination, cutoff: LocalDate?, cardId: String = "c1") =
        CardPayment(
            id = "p$amount", householdId = "h", ownerId = "u", cardId = cardId, cutoffDate = cutoff, amount = amount,
            paidAt = Instant.parse("2026-11-01T12:00:00Z"), method = PaymentMethod.TRANSFER, destination = destination,
        )

    @Test
    fun `un adelanto baja el pago del ciclo abierto de su corte`() {
        val result = listOf(snapshot()).withCredits(listOf(credit(2000.0, PaymentDestination.NEXT_CYCLE, cycleEnd)))
        assertEquals(1000.0, result.single().estimate!!.cycleTotal, 0.001)
    }

    @Test
    fun `un adelanto de otro corte o de otra tarjeta no cuenta`() {
        val result = listOf(snapshot()).withCredits(
            listOf(
                credit(700.0, PaymentDestination.NEXT_CYCLE, LocalDate(2026, 12, 4)),
                credit(800.0, PaymentDestination.NEXT_CYCLE, cycleEnd, cardId = "otra"),
            ),
        )
        assertEquals(3000.0, result.single().estimate!!.cycleTotal, 0.001)
    }

    @Test
    fun `un abono a capital baja lo que falta de las compras a meses`() {
        val result = listOf(snapshot()).withCredits(listOf(credit(500.0, PaymentDestination.CAPITAL, null)))
        assertEquals(4500.0, result.single().estimate!!.msiRemaining, 0.001)
        assertEquals(3000.0, result.single().estimate!!.cycleTotal, 0.001)
    }

    @Test
    fun `los descuentos nunca dejan un total negativo`() {
        val result = listOf(snapshot(cycleTotal = 100.0, msiRemaining = 50.0)).withCredits(
            listOf(credit(900.0, PaymentDestination.NEXT_CYCLE, cycleEnd), credit(900.0, PaymentDestination.CAPITAL, null)),
        )
        assertEquals(0.0, result.single().estimate!!.cycleTotal, 0.001)
        assertEquals(0.0, result.single().estimate!!.msiRemaining, 0.001)
    }

    @Test
    fun `pagar de mas parte el pago entre el corte y el excedente`() {
        assertEquals(
            listOf(PaymentPart(5000.0, PaymentDestination.CYCLE), PaymentPart(2000.0, PaymentDestination.CAPITAL)),
            splitPayment(7000.0, 5000.0, PaymentDestination.CAPITAL),
        )
    }

    @Test
    fun `con el corte pagado todo el monto es excedente`() {
        assertEquals(
            listOf(PaymentPart(2000.0, PaymentDestination.NEXT_CYCLE)),
            splitPayment(2000.0, 0.0, PaymentDestination.NEXT_CYCLE),
        )
    }

    @Test
    fun `un pago menor al corte no genera excedente`() {
        assertEquals(
            listOf(PaymentPart(1500.0, PaymentDestination.CYCLE)),
            splitPayment(1500.0, 5000.0, PaymentDestination.CAPITAL),
        )
    }
}
