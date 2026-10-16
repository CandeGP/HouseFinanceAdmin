package mx.finanzas.pareja.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.cycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.data.CardPayment
import mx.finanzas.pareja.data.MonthSpend
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.domain.CardPaymentDraft
import mx.finanzas.pareja.domain.CardDraft
import mx.finanzas.pareja.domain.CardEditDraft
import mx.finanzas.pareja.domain.CardField
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.card
import mx.finanzas.pareja.ui.cards.CardsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository().apply {
        cardList = listOf(
            card("activa"),
            card("vieja").copy(archived = true),
        )
    }

    private fun vm() = CardsViewModel(repo, userId = "u1", householdId = "h1")

    @Test
    fun `separa las tarjetas activas de las archivadas`() {
        val s = vm().state.value
        assertEquals(listOf("activa"), s.cards.map { it.id })
        assertEquals(listOf("vieja"), s.archived.map { it.id })
    }

    @Test
    fun `archivar mueve la tarjeta a archivadas sin perderla`() {
        val vm = vm()
        vm.archive("activa")
        val s = vm.state.value
        assertEquals(emptyList<String>(), s.cards.map { it.id })
        assertEquals(setOf("activa", "vieja"), s.archived.map { it.id }.toSet())
    }

    @Test
    fun `editar guarda corte, pago, limite, presupuesto y color`() {
        val vm = vm()
        vm.saveEdit(
            "activa",
            CardEditDraft(
                card = CardDraft(
                    name = "Plata", bank = "Plata", last4 = "0931",
                    limitText = "20000", cutoffText = "10", dueText = "5",
                ),
                budgetText = "2000",
                color = "#7c3aed",
            ),
        )

        val saved = vm.state.value.cards.single()
        assertEquals(10, saved.cutoffDay)
        assertEquals(5, saved.dueDay)
        assertEquals(20000.0, saved.creditLimit, 0.0)
        assertEquals(2000.0, saved.monthlyBudget!!, 0.0)
        assertEquals("#7C3AED", saved.color)
        assertEquals(true, vm.state.value.edited)
    }

    @Test
    fun `una edicion invalida no llega al servidor y muestra los errores`() {
        val vm = vm()
        vm.saveEdit(
            "activa",
            CardEditDraft(CardDraft(name = "", bank = "Plata", last4 = "0931", limitText = "1", cutoffText = "99", dueText = "5")),
        )

        assertEquals(emptyList<String>(), repo.editedCards.map { it.first })
        assertEquals(true, vm.state.value.editErrors.containsKey(CardField.NAME))
        assertEquals(true, vm.state.value.editErrors.containsKey(CardField.CUTOFF))
        assertEquals(false, vm.state.value.edited)
    }

    @Test
    fun `quitar presupuesto y color en la edicion los limpia`() {
        val vm = vm()
        val draft = CardDraft(name = "Plata", bank = "Plata", last4 = "0931", limitText = "15000", cutoffText = "18", dueText = "19")
        vm.saveEdit("activa", CardEditDraft(draft, budgetText = "1500", color = "#DC2626"))
        vm.saveEdit("activa", CardEditDraft(draft, budgetText = "", color = null))

        val saved = vm.state.value.cards.single()
        assertEquals(null, saved.monthlyBudget)
        assertEquals(null, saved.color)
    }

    @Test
    fun `fijar un presupuesto lo guarda y trae el gasto del mes`() {
        repo.monthSpends["activa"] = MonthSpend(
            monthStart = LocalDate(2026, 10, 1), monthEnd = LocalDate(2026, 10, 31),
            spent = 1700.0, budget = 2000.0, pct = 85.0,
        )
        val vm = vm()
        vm.setBudget("activa", 2000.0)

        assertEquals(listOf("activa" to 2000.0), repo.budgetsSet)
        assertEquals(2000.0, vm.state.value.cards.single().monthlyBudget!!, 0.0)
        assertEquals(1700.0, vm.state.value.spend["activa"]!!.spent, 0.0)
    }

    @Test
    fun `quitar el presupuesto limpia el gasto mostrado`() {
        val vm = vm()
        vm.setBudget("activa", 1500.0)
        vm.setBudget("activa", null)

        assertEquals(null, vm.state.value.cards.single().monthlyBudget)
        assertEquals(emptyMap<String, MonthSpend>(), vm.state.value.spend)
    }

    @Test
    fun `un presupuesto de cero se rechaza sin llamar al servidor`() {
        val vm = vm()
        vm.setBudget("activa", 0.0)

        assertEquals(emptyList<Pair<String, Double?>>(), repo.budgetsSet)
        assertEquals("El presupuesto debe ser mayor a cero", vm.state.value.message)
    }

    private fun payment(id: String, cardId: String = "activa") = CardPayment(
        id = id, householdId = "h1", ownerId = "u1", cardId = cardId, cutoffDate = LocalDate(2026, 10, 10),
        amount = 300.0, paidAt = Instant.parse("2026-10-12T18:00:00Z"), method = PaymentMethod.CASH,
    )

    @Test
    fun `carga el estado de pago de las tarjetas activas y los pagos del ciclo`() {
        repo.cycleStatuses["activa"] = cycleStatus(CardPayStatus.PARTIAL, paid = 300.0)
        repo.cardPaymentList += payment("p1")
        val s = vm().state.value

        assertEquals(CardPayStatus.PARTIAL, s.statuses["activa"]!!.status)
        assertEquals(listOf("p1"), s.payments["activa"].orEmpty().map { it.id })
    }

    @Test
    fun `registrar un pago lo liga al ciclo, cierra el formulario y recarga`() {
        repo.cycleStatuses["activa"] = cycleStatus(CardPayStatus.PENDING)
        val vm = vm()
        vm.registerPayment(
            vm.state.value.cards.single(), vm.state.value.statuses["activa"]!!,
            CardPaymentDraft(amountText = "1,000", method = PaymentMethod.TRANSFER, notes = " BBVA app "),
        )

        val added = repo.addedCardPayments.single()
        assertEquals("activa", added.cardId)
        assertEquals("u1", added.ownerId)
        assertEquals("h1", added.householdId)
        assertEquals(LocalDate(2026, 10, 10), added.cutoffDate)
        assertEquals(1000.0, added.amount, 0.0)
        assertEquals("BBVA app", added.notes)
        assertEquals(true, vm.state.value.paymentSaved)
    }

    @Test
    fun `un monto invalido no llega al servidor y muestra el error`() {
        repo.cycleStatuses["activa"] = cycleStatus(CardPayStatus.PENDING)
        val vm = vm()
        vm.registerPayment(vm.state.value.cards.single(), vm.state.value.statuses["activa"]!!, CardPaymentDraft("0"))

        assertEquals(emptyList<Any>(), repo.addedCardPayments)
        assertEquals("Monto inválido", vm.state.value.paymentError)
        assertEquals(false, vm.state.value.paymentSaved)
    }

    @Test
    fun `no se registra un pago en una tarjeta ajena`() {
        repo.cardList = listOf(card("ajena").copy(ownerId = "u2"))
        repo.cycleStatuses["ajena"] = cycleStatus(CardPayStatus.PENDING)
        val vm = vm()
        vm.registerPayment(vm.state.value.cards.single(), vm.state.value.statuses["ajena"]!!, CardPaymentDraft("100"))

        assertEquals(emptyList<Any>(), repo.addedCardPayments)
    }

    private val oct16 = LocalDate(2026, 10, 16)
    private fun vmOn(today: LocalDate) = CardsViewModel(repo, userId = "u1", householdId = "h1", today = today)

    @Test
    fun `registrar un pago de dias atras lo guarda con esa fecha`() {
        repo.cycleStatuses["activa"] = cycleStatus(CardPayStatus.PENDING)
        val vm = vmOn(oct16)
        vm.registerPayment(
            vm.state.value.cards.single(), vm.state.value.statuses["activa"]!!,
            CardPaymentDraft(amountText = "100", date = LocalDate(2026, 10, 10)),
        )

        // 10 oct a mediodía en Ciudad de México (UTC-6) = 18:00 UTC
        assertEquals(Instant.parse("2026-10-10T18:00:00Z"), repo.addedCardPayments.single().paidAt)
    }

    @Test
    fun `una fecha futura no llega al servidor al registrar un pago`() {
        repo.cycleStatuses["activa"] = cycleStatus(CardPayStatus.PENDING)
        val vm = vmOn(oct16)
        vm.registerPayment(
            vm.state.value.cards.single(), vm.state.value.statuses["activa"]!!,
            CardPaymentDraft(amountText = "100", date = LocalDate(2026, 10, 20)),
        )

        assertEquals(emptyList<Any>(), repo.addedCardPayments)
        assertEquals("La fecha no puede ser futura", vm.state.value.paymentError)
    }

    @Test
    fun `editar un pago cambia monto, fecha, forma de pago y nota`() {
        repo.cycleStatuses["activa"] = cycleStatus(CardPayStatus.PARTIAL, paid = 300.0)
        repo.cardPaymentList += payment("p1")
        val vm = vmOn(oct16)
        vm.updatePayment(
            vm.state.value.payments["activa"]!!.single(),
            CardPaymentDraft(amountText = "450", method = PaymentMethod.TRANSFER, notes = " ajuste ", date = LocalDate(2026, 10, 11)),
        )

        val (id, patch) = repo.updatedCardPayments.single()
        assertEquals("p1", id)
        assertEquals(450.0, patch.amount, 0.0)
        assertEquals(PaymentMethod.TRANSFER, patch.method)
        assertEquals("ajuste", patch.notes)
        assertEquals(Instant.parse("2026-10-11T18:00:00Z"), patch.paidAt)
        assertEquals(true, vm.state.value.paymentSaved)
        assertEquals(450.0, vm.state.value.payments["activa"]!!.single().amount, 0.0)
    }

    @Test
    fun `editar sin cambiar el dia conserva la hora original del pago`() {
        repo.cycleStatuses["activa"] = cycleStatus(CardPayStatus.PARTIAL, paid = 300.0)
        repo.cardPaymentList += payment("p1") // 12 oct 18:00 UTC = 12 oct 12:00 en Ciudad de México
        val vm = vmOn(oct16)
        vm.updatePayment(
            vm.state.value.payments["activa"]!!.single(),
            CardPaymentDraft(amountText = "320", date = LocalDate(2026, 10, 12)),
        )

        assertEquals(Instant.parse("2026-10-12T18:00:00Z"), repo.updatedCardPayments.single().second.paidAt)
    }

    @Test
    fun `no se edita un pago ajeno ni con datos invalidos`() {
        repo.cycleStatuses["activa"] = cycleStatus(CardPayStatus.PARTIAL, paid = 300.0)
        repo.cardPaymentList += payment("p1")
        val vm = vmOn(oct16)
        val own = vm.state.value.payments["activa"]!!.single()

        vm.updatePayment(own.copy(ownerId = "u2"), CardPaymentDraft("100"))
        assertEquals(emptyList<Any>(), repo.updatedCardPayments)

        vm.updatePayment(own, CardPaymentDraft("0"))
        assertEquals(emptyList<Any>(), repo.updatedCardPayments)
        assertEquals("Monto inválido", vm.state.value.paymentError)
    }

    @Test
    fun `borrar un pago lo quita y recarga`() {
        repo.cycleStatuses["activa"] = cycleStatus(CardPayStatus.PARTIAL, paid = 300.0)
        repo.cardPaymentList += payment("p1")
        val vm = vm()
        vm.deletePayment("p1")

        assertEquals(listOf("p1"), repo.deletedCardPayments)
        assertEquals(emptyList<String>(), vm.state.value.payments["activa"].orEmpty().map { it.id })
    }

    @Test
    fun `restaurar devuelve la tarjeta archivada a las activas`() {
        val vm = vm()
        vm.restore("vieja")
        val s = vm.state.value
        assertEquals(setOf("activa", "vieja"), s.cards.map { it.id }.toSet())
        assertEquals(emptyList<String>(), s.archived.map { it.id })
        assertEquals(listOf("vieja"), repo.restoredCards)
    }
}
