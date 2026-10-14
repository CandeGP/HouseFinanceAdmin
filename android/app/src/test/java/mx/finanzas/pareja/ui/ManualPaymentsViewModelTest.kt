package mx.finanzas.pareja.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.data.ManualPayment
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.ui.dashboard.Scope
import mx.finanzas.pareja.ui.payments.ManualPaymentsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ManualPaymentsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val zone = TimeZone.of("America/Mexico_City")

    private fun payment(id: String, owner: String, at: String, evidence: String? = null) = ManualPayment(
        id = id, householdId = "h1", ownerId = owner, method = PaymentMethod.CASH, amount = 100.0,
        paidAt = Instant.parse(at), concept = "Concepto $id", evidencePath = evidence,
    )

    private val repo = FakeFinanceRepository().apply {
        paymentList = mutableListOf(
            payment("mine", "u1", "2026-10-12T20:30:00Z", evidence = "h1/u1/a.jpg"),
            payment("partner", "u2", "2026-10-20T18:00:00Z"),
            payment("sep", "u1", "2026-09-30T20:00:00Z"),
        )
    }

    private fun vm() = ManualPaymentsViewModel(repo, userId = "u1", today = LocalDate(2026, 10, 14), zone = zone)

    @Test
    fun `carga el mes actual usando los limites en hora de Mexico`() {
        val s = vm().state.value
        assertEquals(setOf("mine", "partner"), s.items.map { it.id }.toSet())
        assertEquals(
            Instant.parse("2026-10-01T06:00:00Z") to Instant.parse("2026-11-01T06:00:00Z"),
            repo.lastPaymentRange,
        )
    }

    @Test
    fun `el alcance Mios filtra por duenio`() {
        val vm = vm()
        vm.setScope(Scope.MINE)
        assertEquals(listOf("mine"), vm.state.value.items.map { it.id })
    }

    @Test
    fun `mes anterior carga septiembre`() {
        val vm = vm()
        vm.previousMonth()
        assertEquals(9, vm.state.value.month)
        assertEquals(listOf("sep"), vm.state.value.items.map { it.id })
    }

    @Test
    fun `eliminar un pago propio borra tambien su evidencia`() {
        val vm = vm()
        vm.delete("mine")
        assertEquals(listOf("mine"), repo.deletedPayments)
        assertEquals(listOf("h1/u1/a.jpg"), repo.deletedEvidence)
    }

    @Test
    fun `no elimina pagos de otra persona`() {
        val vm = vm()
        vm.delete("partner")
        assertEquals(emptyList<String>(), repo.deletedPayments)
    }
}
