package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.Loan
import mx.finanzas.pareja.data.LoanDirection
import mx.finanzas.pareja.data.LoanRepayment
import mx.finanzas.pareja.data.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanRemindersTest {
    private val today = LocalDate(2026, 10, 30)

    private fun view(
        id: String, person: String = "Hermana", amount: Double = 300.0, on: LocalDate,
        direction: LoanDirection = LoanDirection.LENT, paid: Double = 0.0, forgiven: Boolean = false,
    ): LoanView {
        val loan = Loan(
            id = id, householdId = "h", ownerId = "u1", person = person, direction = direction, amount = amount,
            occurredOn = on, method = PaymentMethod.TRANSFER,
            forgivenAt = if (forgiven) Instant.parse("2026-10-20T12:00:00Z") else null,
        )
        val repayments = if (paid > 0) {
            listOf(LoanRepayment("r$id", "h", "u1", id, paid, LocalDate(2026, 10, 20), PaymentMethod.CASH))
        } else emptyList()
        return LoanView(loan, repayments)
    }

    @Test
    fun `antes de 15 dias no avisa`() {
        assertTrue(loanReminders(listOf(view("l1", on = LocalDate(2026, 10, 16))), today).isEmpty()) // 14 días
    }

    @Test
    fun `a los 15 dias avisa cuanto te deben y hace cuanto`() {
        val alert = loanReminders(listOf(view("l1", on = LocalDate(2026, 10, 15), paid = 100.0)), today).single()

        assertEquals(AlertKind.LOAN, alert.kind)
        assertEquals("loan:l1:15", alert.key)
        assertEquals("Hermana te debe ${formatMxn(200.0)}", alert.title)
        assertTrue(alert.text.contains("15 días"))
        assertEquals(200.0, alert.amount!!, 0.0)
    }

    @Test
    fun `si es lo que tu debes lo dice al reves`() {
        val alert = loanReminders(
            listOf(view("l2", person = "Papá", amount = 80.0, on = LocalDate(2026, 10, 1), direction = LoanDirection.BORROWED)),
            today,
        ).single()
        assertEquals("Le debes ${formatMxn(80.0)} a Papá", alert.title)
    }

    @Test
    fun `el aviso se repite cada 15 dias y no antes`() {
        fun key(on: LocalDate) = loanReminders(listOf(view("l1", on = on)), today).singleOrNull()?.key
        assertEquals("loan:l1:15", key(LocalDate(2026, 10, 10))) // 20 días
        assertEquals("loan:l1:30", key(LocalDate(2026, 9, 28))) // 32 días
    }

    @Test
    fun `liquidados y perdonados no avisan`() {
        val settled = view("l1", on = LocalDate(2026, 10, 1), amount = 100.0, paid = 100.0)
        val forgiven = view("l2", on = LocalDate(2026, 10, 1), forgiven = true)
        assertTrue(loanReminders(listOf(settled, forgiven), today).isEmpty())
    }

    @Test
    fun `primero el mas viejo`() {
        val alerts = loanReminders(
            listOf(view("nuevo", person = "Ana", on = LocalDate(2026, 10, 14)), view("viejo", person = "Beto", on = LocalDate(2026, 9, 1))),
            today,
        )
        assertEquals(listOf("loan:viejo:45", "loan:nuevo:15"), alerts.map { it.key })
    }
}
