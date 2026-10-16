package mx.finanzas.pareja.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.card
import mx.finanzas.pareja.data.CardPayment
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.tx
import mx.finanzas.pareja.ui.statements.ImportStep
import mx.finanzas.pareja.ui.statements.StatementImportViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatementImportViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val dollar = '$'

    private val text = """
        Número de Tarjeta 531722XXXXXX0931
        Periodo 19-ago-2026 al 18-sep-2026
        Fecha de corte 18-sep-2026
        Fecha límite de pago 1 lun, 19-oct-2026
        Pago mínimo 4 ${dollar}200.00
        Límite de crédito ${dollar}15,000.00
        Plata Card
        22-jun-2026 TIENDA EJEMPLO LEON ${dollar}2,034.00 ${dollar}678.00 ${dollar}678.00 2 de 3 0%
        04-sep-2026 05-sep-2026 5841 PP*EJEMPLO FACEBOOK + 79.00 MXN 1.00 + ${dollar}79.00
        18-ago-2026 19-ago-2026 5841 PAYPAL *EJEMPLO TWITCH + 69.60 MXN 1.00 + ${dollar}69.60
    """.trimIndent()

    private var fileCounter = 0

    private val repo = FakeFinanceRepository().apply {
        cardList = listOf(
            card("c1").copy(last4 = "0931"),
            card("c2").copy(last4 = "7777", altLast4 = listOf("0931x".take(4))),
            card("partner").copy(ownerId = "u2"),
        )
        txList = mutableListOf(
            tx("t-fb", 79.0, cardId = "c1", date = LocalDate(2026, 9, 4)).copy(merchant = "Pp*Ejemplo Facebook"),
        )
    }

    private fun vm() = StatementImportViewModel(repo, userId = "u1", householdId = "h1", newFileId = { "f-${++fileCounter}" })

    @Test
    fun `un pdf sin texto se explica como imagen escaneada`() {
        val vm = vm()
        vm.onPdfRead("   ", null)
        assertEquals(ImportStep.PICK, vm.state.value.step)
        assertTrue(vm.state.value.message!!.contains("imagen", ignoreCase = true))
    }

    @Test
    fun `un texto que no es estado de cuenta se rechaza`() {
        val vm = vm()
        vm.onPdfRead("Factura de luz CFE periodo bimestral", null)
        assertEquals(ImportStep.PICK, vm.state.value.step)
        assertNotNull(vm.state.value.message)
    }

    @Test
    fun `reconoce la tarjeta por los ultimos 4 y concilia`() {
        val vm = vm()
        vm.onPdfRead(text, null)

        val s = vm.state.value
        assertEquals(ImportStep.REVIEW, s.step)
        assertEquals("c1", s.cardId)
        val r = s.reconciliation!!
        assertEquals(1, r.matched.size)
        assertEquals(1, r.missingInApp.size)
        assertEquals("PAYPAL *EJEMPLO TWITCH", r.missingInApp.single().description)
        assertEquals(1, r.missingInstallments.size)
    }

    @Test
    fun `solo ofrece tarjetas propias activas`() {
        assertEquals(listOf("c1", "c2"), vm().state.value.cards.map { it.id })
    }

    @Test
    fun `si no reconoce la tarjeta pide elegirla y luego concilia`() {
        repo.cardList = listOf(card("c1").copy(last4 = "1111"), card("c2").copy(last4 = "2222"))
        val vm = vm()
        vm.onPdfRead(text, null)

        assertNull(vm.state.value.cardId)
        assertNull(vm.state.value.reconciliation)
        assertEquals(ImportStep.REVIEW, vm.state.value.step)

        vm.selectCard("c1")
        assertNotNull(vm.state.value.reconciliation)
    }

    @Test
    fun `importar manda solo los movimientos faltantes y las compras a meses`() {
        val vm = vm()
        vm.onPdfRead(text, null)
        vm.import()

        val call = repo.importCalls.single()
        assertEquals("c1", call.cardId)
        assertEquals(listOf("PAYPAL *EJEMPLO TWITCH"), call.movements.map { it.description })
        assertEquals(69.6, call.movements.single().amount, 0.0)
        assertEquals(1, call.installments.size)
        assertEquals(2, call.installments.single().current)
        assertEquals(LocalDate(2026, 9, 18), call.summary.cutoffDate)
        assertEquals(15_000.0, call.summary.creditLimit!!, 0.0)
        assertEquals(ImportStep.DONE, vm.state.value.step)
    }

    @Test
    fun `un movimiento desmarcado no se importa`() {
        val vm = vm()
        vm.onPdfRead(text, null)
        vm.toggleMovement(0)
        vm.import()
        assertTrue(repo.importCalls.single().movements.isEmpty())
    }

    // Mismo estado, con un pago por SPEI y una bonificación (esta no es un pago).
    private val textWithPayments = text + "\n" +
        "17-sep-2026 17-sep-2026 0931 Pago por transferencia SPEI − 5,560.26 MXN 1.00 − ${dollar}5,560.26\n" +
        "05-sep-2026 12-sep-2026 0931 Abono de \"Amazon A Meses\" -${dollar}400.58"

    private fun existingPayment(amount: Double) = CardPayment(
        id = "p-old", householdId = "h1", ownerId = "u1", cardId = "c1", cutoffDate = LocalDate(2026, 8, 18),
        amount = amount, paidAt = Instant.parse("2026-09-17T18:00:00Z"), method = PaymentMethod.CASH,
    )

    @Test
    fun `detecta los pagos del estado y deja fuera las bonificaciones`() {
        val vm = vm()
        vm.onPdfRead(textWithPayments, null)

        val payments = vm.state.value.payments
        assertEquals(1, payments.size)
        assertEquals(5560.26, payments.single().amount, 0.0)
        assertEquals(false, payments.single().alreadyRegistered)
    }

    @Test
    fun `importar registra los pagos como pago del corte anterior con la fecha del abono`() {
        val vm = vm()
        vm.onPdfRead(textWithPayments, null)
        vm.import()

        val added = repo.addedCardPayments.single()
        assertEquals("c1", added.cardId)
        assertEquals("u1", added.ownerId)
        assertEquals(LocalDate(2026, 8, 18), added.cutoffDate) // el estado es del corte 18 sep; paga el del 18 ago
        assertEquals(5560.26, added.amount, 0.0)
        assertEquals(Instant.parse("2026-09-17T18:00:00Z"), added.paidAt)
        assertEquals(PaymentMethod.TRANSFER, added.method)
        assertEquals(1, vm.state.value.paymentsRegistered)
        assertEquals(ImportStep.DONE, vm.state.value.step)
    }

    @Test
    fun `un pago desmarcado no se registra`() {
        val vm = vm()
        vm.onPdfRead(textWithPayments, null)
        vm.togglePayment(0)
        vm.import()

        assertTrue(repo.addedCardPayments.isEmpty())
        assertEquals(0, vm.state.value.paymentsRegistered)
    }

    @Test
    fun `un pago ya capturado a mano no se duplica`() {
        repo.cardPaymentList += existingPayment(5560.26)
        val vm = vm()
        vm.onPdfRead(textWithPayments, null)

        assertEquals(true, vm.state.value.payments.single().alreadyRegistered)
        vm.import()
        assertTrue(repo.addedCardPayments.isEmpty())
    }

    @Test
    fun `si falla el registro de pagos el estado igual queda importado y se avisa`() {
        repo.cardPaymentFailWith = Exception("Sin conexión con el servidor")
        val vm = vm()
        vm.onPdfRead(textWithPayments, ByteArray(10))
        vm.import()

        assertEquals(1, repo.importCalls.size)
        assertTrue(repo.deletedEvidence.isEmpty()) // el PDF ya quedó ligado al estado: no se borra
        assertEquals(ImportStep.DONE, vm.state.value.step)
        assertEquals(0, vm.state.value.paymentsRegistered)
        assertNotNull(vm.state.value.message)
    }

    @Test
    fun `importar sin tarjeta elegida avisa y no escribe`() {
        repo.cardList = listOf(card("c1").copy(last4 = "1111"))
        val vm = vm()
        vm.onPdfRead(text, null)
        vm.import()
        assertTrue(repo.importCalls.isEmpty())
        assertNotNull(vm.state.value.message)
    }

    @Test
    fun `sube el pdf y guarda su ruta en el estado`() {
        val vm = vm()
        vm.onPdfRead(text, ByteArray(500))
        vm.import()

        assertEquals("h1/u1/statement-f-1.pdf", repo.uploadedEvidence.single().first)
        assertEquals("application/pdf", repo.uploadedEvidence.single().third)
        assertEquals("h1/u1/statement-f-1.pdf", repo.importCalls.single().summary.filePath)
    }

    @Test
    fun `si falla la importacion se borra el pdf ya subido`() {
        val vm = vm()
        vm.onPdfRead(text, ByteArray(10))
        repo.failWith = Exception("Sin conexión con el servidor")
        vm.import()

        assertEquals(listOf("h1/u1/statement-f-1.pdf"), repo.deletedEvidence)
        assertEquals(ImportStep.REVIEW, vm.state.value.step)
        assertNotNull(vm.state.value.message)
    }
}
