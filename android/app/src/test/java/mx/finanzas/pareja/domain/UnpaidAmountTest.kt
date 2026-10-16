package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UnpaidAmountTest {
    private fun status(kind: CardPayStatus, remaining: Double) = CardCycleStatus(
        cutoffDate = LocalDate(2026, 10, 4), dueDate = LocalDate(2026, 10, 26), targetAmount = 3912.02,
        targetSource = "statement", paidAmount = 3912.02 - remaining, remaining = remaining, status = kind,
    )

    @Test
    fun `pendiente, vencido, parcial y solo minimo suman lo que falta`() {
        listOf(CardPayStatus.PENDING, CardPayStatus.OVERDUE, CardPayStatus.PARTIAL, CardPayStatus.MINIMUM_ONLY)
            .forEach { assertEquals(it.name, 3912.02, status(it, 3912.02).unpaidAmount, 0.0) }
    }

    @Test
    fun `pagado, sin registro o sin estado no suman nada`() {
        assertEquals(0.0, status(CardPayStatus.PAID, 0.0).unpaidAmount, 0.0)
        assertEquals(0.0, status(CardPayStatus.UNTRACKED, 1408.0).unpaidAmount, 0.0)
        assertEquals(0.0, (null as CardCycleStatus?).unpaidAmount, 0.0)
    }
}
