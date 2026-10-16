package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.Loan
import mx.finanzas.pareja.data.LoanDirection
import mx.finanzas.pareja.data.LoanRepayment
import mx.finanzas.pareja.data.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class LoansTest {
    private fun loan(
        id: String, person: String = "Hermana", amount: Double = 300.0,
        direction: LoanDirection = LoanDirection.LENT, forgiven: Boolean = false,
        on: LocalDate = LocalDate(2026, 10, 12),
    ) = Loan(
        id = id, householdId = "h1", ownerId = "u1", person = person, direction = direction, amount = amount,
        occurredOn = on, method = PaymentMethod.TRANSFER,
        forgivenAt = if (forgiven) Instant.parse("2026-10-14T12:00:00Z") else null,
    )

    private fun repayment(id: String, loanId: String, amount: Double) = LoanRepayment(
        id = id, householdId = "h1", ownerId = "u1", loanId = loanId, amount = amount,
        paidOn = LocalDate(2026, 10, 14), method = PaymentMethod.CASH,
    )

    @Test
    fun `un prestamo sin abonos esta abierto y debe todo`() {
        val view = buildLoanViews(listOf(loan("l1")), emptyList()).single()
        assertEquals(LoanState.OPEN, view.state)
        assertEquals(300.0, view.remaining, 0.0)
        assertEquals(0.0, view.repaid, 0.0)
    }

    @Test
    fun `los abonos reducen lo que falta y al cubrirlo queda liquidado`() {
        val partial = buildLoanViews(listOf(loan("l1")), listOf(repayment("r1", "l1", 100.0))).single()
        assertEquals(200.0, partial.remaining, 0.0)
        assertEquals(LoanState.OPEN, partial.state)

        val settled = buildLoanViews(
            listOf(loan("l1")), listOf(repayment("r1", "l1", 100.0), repayment("r2", "l1", 200.0)),
        ).single()
        assertEquals(0.0, settled.remaining, 0.0)
        assertEquals(LoanState.SETTLED, settled.state)
    }

    @Test
    fun `los centavos no dejan saldos fantasma`() {
        val view = buildLoanViews(
            listOf(loan("l1", amount = 0.3)), listOf(repayment("r1", "l1", 0.1), repayment("r2", "l1", 0.2)),
        ).single()
        assertEquals(LoanState.SETTLED, view.state)
        assertEquals(0.0, view.remaining, 0.0)
    }

    @Test
    fun `perdonado no cuenta como deuda aunque falte dinero`() {
        val view = buildLoanViews(listOf(loan("l1", forgiven = true)), emptyList()).single()
        assertEquals(LoanState.FORGIVEN, view.state)
        assertEquals(0.0, personBalances(listOf(view)).single().net, 0.0)
    }

    @Test
    fun `el saldo por persona suma lo prestado y resta lo que te prestaron`() {
        val views = buildLoanViews(
            listOf(
                loan("l1", "Hermana", 300.0),
                loan("l2", "hermana ", 150.0),
                loan("l3", "Hermana", 100.0, LoanDirection.BORROWED),
                loan("l4", "Papá", 80.0, LoanDirection.BORROWED),
            ),
            listOf(repayment("r1", "l1", 100.0)),
        )
        val balances = personBalances(views)

        assertEquals(listOf("Hermana", "Papá"), balances.map { it.person })
        assertEquals(250.0, balances[0].net, 0.0) // 200 + 150 - 100
        assertEquals(-80.0, balances[1].net, 0.0)
        assertEquals(3, balances[0].loans.size)
    }

    @Test
    fun `los liquidados y perdonados quedan al final y a mano`() {
        val views = buildLoanViews(
            listOf(loan("l1", "Ana", 50.0), loan("l2", "Beto", 90.0)),
            listOf(repayment("r1", "l1", 50.0)),
        )
        val balances = personBalances(views)
        assertEquals(listOf("Beto", "Ana"), balances.map { it.person })
        assertEquals("Estás a mano con Ana", balances[1].label())
    }

    @Test
    fun `la etiqueta dice quien debe a quien`() {
        assertEquals("Hermana te debe ${formatMxn(450.0)}", PersonBalance("Hermana", 450.0, emptyList()).label())
        assertEquals("Le debes ${formatMxn(80.0)} a Papá", PersonBalance("Papá", -80.0, emptyList()).label())
    }

    @Test
    fun `los totales separan lo que te deben de lo que debes`() {
        val views = buildLoanViews(
            listOf(
                loan("l1", amount = 300.0), loan("l2", amount = 100.0, direction = LoanDirection.BORROWED),
                loan("l3", amount = 999.0, forgiven = true),
            ),
            listOf(repayment("r1", "l1", 100.0)),
        )
        assertEquals(LoanTotals(owedToYou = 200.0, youOwe = 100.0), loanTotals(views))
    }

    @Test
    fun `la antiguedad se dice en dias`() {
        val today = LocalDate(2026, 10, 16)
        assertEquals("hoy", ageLabel(today, today))
        assertEquals("ayer", ageLabel(LocalDate(2026, 10, 15), today))
        assertEquals("hace 15 días", ageLabel(LocalDate(2026, 10, 1), today))
    }

    private val today = LocalDate(2026, 10, 16)

    @Test
    fun `nuevo prestamo exige persona y monto`() {
        val bad = LoanDraft(person = "  ", amountText = "abc").validate(today) as LoanResult.Invalid
        assertEquals(setOf(LoanField.PERSON, LoanField.AMOUNT), bad.errors.keys)

        val ok = LoanDraft(
            person = " Hermana ", amountText = "$300", direction = LoanDirection.BORROWED,
            method = PaymentMethod.CASH, note = " para la renta ",
        ).validate(today)
        assertEquals(
            LoanResult.Valid("Hermana", 300.0, LoanDirection.BORROWED, PaymentMethod.CASH, "para la renta", today),
            ok,
        )
    }

    @Test
    fun `sin fecha el prestamo es de hoy y una fecha anterior se respeta`() {
        val sinFecha = LoanDraft(person = "Ana", amountText = "50").validate(today) as LoanResult.Valid
        assertEquals(today, sinFecha.occurredOn)

        val antes = LocalDate(2026, 10, 1)
        val conFecha = LoanDraft(person = "Ana", amountText = "50", date = antes).validate(today) as LoanResult.Valid
        assertEquals(antes, conFecha.occurredOn)
    }

    @Test
    fun `una fecha futura se rechaza`() {
        val bad = LoanDraft(person = "Ana", amountText = "50", date = LocalDate(2026, 10, 17))
            .validate(today) as LoanResult.Invalid
        assertEquals(mapOf(LoanField.DATE to "La fecha no puede ser futura"), bad.errors)
    }

    @Test
    fun `un abono no puede pasarse de lo que falta`() {
        val draft = LoanRepaymentDraft(amountText = "250")
        assertEquals(
            LoanRepaymentResult.Invalid("Es más de lo que falta (${formatMxn(200.0)})"),
            draft.validate(remaining = 200.0),
        )
        assertEquals(
            LoanRepaymentResult.Valid(200.0, PaymentMethod.TRANSFER, null),
            LoanRepaymentDraft(amountText = "200").validate(remaining = 200.0),
        )
        assertEquals(LoanRepaymentResult.Invalid("Monto inválido"), LoanRepaymentDraft("0").validate(200.0))
    }
}
