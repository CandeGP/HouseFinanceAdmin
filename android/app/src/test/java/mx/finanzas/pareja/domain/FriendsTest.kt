package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.Loan
import mx.finanzas.pareja.data.LoanDirection
import mx.finanzas.pareja.data.LoanRepayment
import mx.finanzas.pareja.data.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsTest {
    private val me = "u1"

    private fun loan(
        id: String,
        person: String,
        amount: Double,
        direction: LoanDirection = LoanDirection.LENT,
        on: LocalDate = LocalDate(2026, 11, 1),
        owner: String = me,
        note: String? = null,
    ) = Loan(
        id = id, householdId = "h", ownerId = owner, person = person, direction = direction,
        amount = amount, occurredOn = on, method = PaymentMethod.TRANSFER, note = note,
    )

    private fun repayment(loanId: String, amount: Double, on: LocalDate) = LoanRepayment(
        id = "r-$loanId-$amount", householdId = "h", ownerId = me, loanId = loanId,
        amount = amount, paidOn = on, method = PaymentMethod.TRANSFER,
    )

    private fun friends(loans: List<Loan>, repayments: List<LoanRepayment> = emptyList(), partners: Set<String> = emptySet()) =
        buildFriends(personBalances(buildLoanViews(loans, repayments)), partners)

    @Test
    fun `marca como pareja a quien comparte el hogar sin importar mayusculas`() {
        val list = friends(listOf(loan("1", "Luis", 100.0), loan("2", "Marta", 50.0)), partners = setOf("luis"))
        assertTrue(list.first { it.name == "Luis" }.isPartner)
        assertFalse(list.first { it.name == "Marta" }.isPartner)
    }

    @Test
    fun `la ultima actividad es el abono o prestamo mas reciente`() {
        val list = friends(
            listOf(loan("1", "Luis", 100.0, on = LocalDate(2026, 10, 1))),
            listOf(repayment("1", 30.0, LocalDate(2026, 11, 3))),
        )
        assertEquals(LocalDate(2026, 11, 3), list.single().lastActivity)
        assertEquals(1, list.single().openLoans)
    }

    @Test
    fun `una persona sin prestamos abiertos queda saldada`() {
        val list = friends(listOf(loan("1", "Luis", 100.0)), listOf(repayment("1", 100.0, LocalDate(2026, 11, 2))))
        assertTrue(list.single().settled)
        assertEquals(0, list.single().openLoans)
    }

    @Test
    fun `los filtros separan lo que te deben, lo que debes y lo saldado`() {
        val list = friends(
            listOf(
                loan("1", "Luis", 100.0),
                loan("2", "Marta", 50.0, LoanDirection.BORROWED),
                loan("3", "Pablo", 20.0),
            ),
            listOf(repayment("3", 20.0, LocalDate(2026, 11, 2))),
        )
        assertEquals(listOf("Luis"), filterFriends(list, FriendFilter.OWE_YOU, "").map { it.name })
        assertEquals(listOf("Marta"), filterFriends(list, FriendFilter.YOU_OWE, "").map { it.name })
        assertEquals(listOf("Pablo"), filterFriends(list, FriendFilter.SETTLED, "").map { it.name })
        assertEquals(3, filterFriends(list, FriendFilter.ALL, "").size)
    }

    @Test
    fun `la busqueda ignora mayusculas y acentos`() {
        val list = friends(listOf(loan("1", "José", 100.0), loan("2", "Marta", 50.0)))
        assertEquals(listOf("José"), filterFriends(list, FriendFilter.ALL, "  jose ").map { it.name })
    }

    @Test
    fun `el plan de liquidacion cubre lo que falta de cada prestamo abierto propio`() {
        val list = friends(
            listOf(
                loan("1", "Luis", 100.0),
                loan("2", "Luis", 40.0),
                loan("3", "Luis", 10.0, owner = "otro"),
            ),
            listOf(repayment("1", 30.0, LocalDate(2026, 11, 2))),
        )
        val plan = settlementPlan(list.single(), me)
        assertEquals(listOf("1" to 70.0, "2" to 40.0), plan.map { it.first.loan.id to it.second })
    }

    @Test
    fun `el recordatorio menciona el monto y la nota`() {
        val friend = friends(listOf(loan("1", "Luis", 250.0, note = "Dividido: Cena"))).single()
        val text = reminderText(friend)
        assertTrue(text.contains("Luis"))
        assertTrue(text.contains("$250.00"))
        assertTrue(text.contains("Dividido: Cena"))
    }

    @Test
    fun `no hay recordatorio si no te deben`() {
        val friend = friends(listOf(loan("1", "Marta", 50.0, LoanDirection.BORROWED))).single()
        assertEquals(null, reminderOrNull(friend))
    }
}
