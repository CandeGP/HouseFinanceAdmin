package mx.finanzas.pareja.ui

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.data.Loan
import mx.finanzas.pareja.data.LoanDirection
import mx.finanzas.pareja.data.LoanRepayment
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.domain.LoanDraft
import mx.finanzas.pareja.domain.LoanField
import mx.finanzas.pareja.domain.LoanRepaymentDraft
import mx.finanzas.pareja.domain.LoanState
import mx.finanzas.pareja.ui.dashboard.Scope
import mx.finanzas.pareja.ui.loans.LoansViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoansViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val today = LocalDate(2026, 10, 16)

    private fun loan(id: String, person: String, amount: Double, owner: String = "u1") = Loan(
        id = id, householdId = "h1", ownerId = owner, person = person, direction = LoanDirection.LENT,
        amount = amount, occurredOn = LocalDate(2026, 10, 12), method = PaymentMethod.TRANSFER,
    )

    private val repo = FakeFinanceRepository().apply {
        loanList = mutableListOf(
            loan("l1", "Hermana", 300.0),
            loan("l2", "Mamá", 100.0, owner = "u2"),
        )
        loanRepaymentList = mutableListOf(
            LoanRepayment(
                id = "r1", householdId = "h1", ownerId = "u1", loanId = "l1", amount = 100.0,
                paidOn = LocalDate(2026, 10, 14), method = PaymentMethod.CASH,
            ),
        )
    }

    private fun vm() = LoansViewModel(repo, userId = "u1", householdId = "h1", today = today)

    @Test
    fun `carga los saldos por persona de ambos y las personas sugeridas`() {
        val s = vm().state.value
        assertEquals(listOf("Hermana", "Mamá"), s.balances.map { it.person })
        assertEquals(200.0, s.balances[0].net, 0.0)
        assertEquals(300.0, s.totals.owedToYou, 0.0)
        assertEquals(listOf("Hermana", "Mamá"), s.people)
    }

    @Test
    fun `el alcance Mios deja solo mis prestamos`() {
        val vm = vm()
        vm.setScope(Scope.MINE)
        assertEquals(listOf("Hermana"), vm.state.value.balances.map { it.person })
        assertEquals(200.0, vm.state.value.totals.owedToYou, 0.0)
    }

    @Test
    fun `registrar un prestamo lo guarda a mi nombre con la fecha de hoy`() {
        val vm = vm()
        vm.addLoan(LoanDraft(person = " Tío ", amountText = "500", method = PaymentMethod.CASH, note = "gasolina"))

        val added = repo.addedLoans.single()
        assertEquals("Tío", added.person)
        assertEquals(500.0, added.amount, 0.0)
        assertEquals("u1", added.ownerId)
        assertEquals("h1", added.householdId)
        assertEquals(today, added.occurredOn)
        assertEquals(LoanDirection.LENT, added.direction)
        assertEquals("gasolina", added.note)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `un prestamo de dias atras se guarda con su fecha`() {
        val vm = vm()
        vm.addLoan(LoanDraft(person = "Ana", amountText = "80", date = LocalDate(2026, 10, 10)))

        assertEquals(LocalDate(2026, 10, 10), repo.addedLoans.single().occurredOn)
    }

    @Test
    fun `una fecha futura no llega al servidor`() {
        val vm = vm()
        vm.addLoan(LoanDraft(person = "Ana", amountText = "80", date = LocalDate(2026, 10, 20)))

        assertTrue(repo.addedLoans.isEmpty())
        assertEquals(setOf(LoanField.DATE), vm.state.value.loanErrors.keys)
    }

    @Test
    fun `un prestamo invalido no llega al servidor y muestra los errores`() {
        val vm = vm()
        vm.addLoan(LoanDraft(person = "", amountText = "0"))

        assertTrue(repo.addedLoans.isEmpty())
        assertEquals(setOf(LoanField.PERSON, LoanField.AMOUNT), vm.state.value.loanErrors.keys)
        assertEquals(false, vm.state.value.saved)
    }

    @Test
    fun `abonar registra el pago con la fecha de hoy`() {
        val vm = vm()
        val view = vm.state.value.balances.first { it.person == "Hermana" }.loans.single()
        vm.addRepayment(view, LoanRepaymentDraft(amountText = "50", method = PaymentMethod.CASH))

        val added = repo.addedLoanRepayments.single()
        assertEquals("l1", added.loanId)
        assertEquals(50.0, added.amount, 0.0)
        assertEquals(today, added.paidOn)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `un abono mayor a lo que falta se rechaza sin llamar al servidor`() {
        val vm = vm()
        val view = vm.state.value.balances.first { it.person == "Hermana" }.loans.single()
        vm.addRepayment(view, LoanRepaymentDraft(amountText = "250"))

        assertTrue(repo.addedLoanRepayments.isEmpty())
        assertTrue(vm.state.value.repaymentError!!.startsWith("Es más de lo que falta"))
    }

    @Test
    fun `no se abona a un prestamo ajeno`() {
        val vm = vm()
        val view = vm.state.value.balances.first { it.person == "Mamá" }.loans.single()
        vm.addRepayment(view, LoanRepaymentDraft(amountText = "10"))

        assertTrue(repo.addedLoanRepayments.isEmpty())
    }

    @Test
    fun `perdonar saca el prestamo de la deuda y reabrirlo la devuelve`() {
        val vm = vm()
        vm.setForgiven("l1", true)
        assertEquals(100.0, vm.state.value.totals.owedToYou, 0.0) // solo queda lo de Mamá
        assertEquals(LoanState.FORGIVEN, vm.state.value.balances.first { it.person == "Hermana" }.loans.single().state)

        vm.setForgiven("l1", false)
        assertEquals(300.0, vm.state.value.totals.owedToYou, 0.0)
    }

    @Test
    fun `borrar un prestamo propio lo quita con sus abonos y uno ajeno no`() {
        val vm = vm()
        vm.deleteLoan("l2") // de la pareja
        assertTrue(repo.deletedLoans.isEmpty())

        vm.deleteLoan("l1")
        assertEquals(listOf("l1"), repo.deletedLoans)
        assertEquals(listOf("Mamá"), vm.state.value.balances.map { it.person })
    }

    @Test
    fun `borrar un abono sube de nuevo lo que falta`() {
        val vm = vm()
        vm.deleteRepayment("l1", "r1")

        assertEquals(listOf("r1"), repo.deletedLoanRepayments)
        assertEquals(300.0, vm.state.value.balances.first { it.person == "Hermana" }.net, 0.0)
    }
}
