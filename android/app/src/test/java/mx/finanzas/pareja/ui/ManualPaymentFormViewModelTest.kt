package mx.finanzas.pareja.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import mx.finanzas.pareja.FakeFinanceRepository
import mx.finanzas.pareja.MainDispatcherRule
import mx.finanzas.pareja.data.ManualPayment
import mx.finanzas.pareja.data.PaymentMethod
import mx.finanzas.pareja.domain.PaymentField
import mx.finanzas.pareja.ui.payments.ManualPaymentFormViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ManualPaymentFormViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val repo = FakeFinanceRepository()
    private val zone = TimeZone.of("America/Mexico_City")
    private var fileCounter = 0

    private fun vm(editingId: String? = null) = ManualPaymentFormViewModel(
        repo = repo, userId = "u1", householdId = "h1", editingId = editingId,
        today = LocalDate(2026, 10, 14), zone = zone, newFileId = { "file-${++fileCounter}" },
    )

    private fun fill(vm: ManualPaymentFormViewModel) {
        vm.onDraftChange(vm.state.value.draft.copy(amountText = "350", timeText = "14:30", concept = "Mecánico"))
    }

    private fun existing(owner: String = "u1", evidence: String? = "h1/u1/old.jpg") = ManualPayment(
        id = "p1", householdId = "h1", ownerId = owner, method = PaymentMethod.TRANSFER, amount = 500.0,
        paidAt = Instant.parse("2026-10-12T20:30:00Z"), concept = "Renta", evidencePath = evidence,
    )

    @Test
    fun `guardar sin evidencia registra el pago a mi nombre`() {
        val vm = vm()
        fill(vm)
        vm.save()

        val added = repo.addedPayments.single()
        assertEquals("u1", added.ownerId)
        assertEquals("h1", added.householdId)
        // fecha por defecto = hoy (14 oct) y 14:30 en hora de Ciudad de México = 20:30 UTC
        assertEquals(Instant.parse("2026-10-14T20:30:00Z"), added.paidAt)
        assertNull(added.evidencePath)
        assertTrue(repo.uploadedEvidence.isEmpty())
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `guardar con evidencia la sube y guarda su ruta`() {
        val vm = vm()
        fill(vm)
        vm.attachEvidence(ByteArray(1234), "image/jpeg")
        vm.save()

        assertEquals("h1/u1/file-1.jpg", repo.uploadedEvidence.single().first)
        assertEquals(1234, repo.uploadedEvidence.single().second)
        assertEquals("h1/u1/file-1.jpg", repo.addedPayments.single().evidencePath)
    }

    @Test
    fun `formato de evidencia no soportado se rechaza`() {
        val vm = vm()
        vm.attachEvidence(ByteArray(10), "image/gif")
        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.hasEvidence)
    }

    @Test
    fun `si falla guardar el pago se limpia la evidencia ya subida`() {
        val vm = vm()
        fill(vm)
        vm.attachEvidence(ByteArray(10), "image/png")
        repo.failWith = Exception("Sin conexión con el servidor")
        vm.save()

        assertFalse(vm.state.value.saved)
        assertEquals(listOf("h1/u1/file-1.png"), repo.deletedEvidence)
    }

    @Test
    fun `un pago invalido no escribe nada`() {
        val vm = vm()
        vm.attachEvidence(ByteArray(10), "image/png")
        vm.save()
        assertNotNull(vm.state.value.errors[PaymentField.CONCEPT])
        assertTrue(repo.addedPayments.isEmpty())
        assertTrue(repo.uploadedEvidence.isEmpty())
    }

    @Test
    fun `editar precarga datos y la url firmada de la evidencia`() {
        repo.paymentList += existing()
        val s = vm("p1").state.value
        assertEquals("Renta", s.draft.concept)
        assertEquals("14:30", s.draft.timeText)
        assertEquals(PaymentMethod.TRANSFER, s.draft.method)
        assertEquals("https://signed.test/h1/u1/old.jpg", s.evidenceUrl)
        assertTrue(s.hasEvidence)
    }

    @Test
    fun `editar sin tocar la evidencia conserva su ruta`() {
        repo.paymentList += existing()
        val vm = vm("p1")
        vm.onDraftChange(vm.state.value.draft.copy(amountText = "550"))
        vm.save()

        val patch = repo.updatedPayments.single().second
        assertEquals(550.0, patch.amount, 0.0)
        assertEquals("h1/u1/old.jpg", patch.evidencePath)
        assertTrue(repo.deletedEvidence.isEmpty())
    }

    @Test
    fun `reemplazar evidencia sube la nueva y borra la anterior`() {
        repo.paymentList += existing()
        val vm = vm("p1")
        vm.attachEvidence(ByteArray(5), "application/pdf")
        vm.save()

        assertEquals("h1/u1/file-1.pdf", repo.updatedPayments.single().second.evidencePath)
        assertEquals(listOf("h1/u1/old.jpg"), repo.deletedEvidence)
    }

    @Test
    fun `quitar evidencia limpia la ruta y borra el archivo`() {
        repo.paymentList += existing()
        val vm = vm("p1")
        vm.removeEvidence()
        vm.save()

        assertNull(repo.updatedPayments.single().second.evidencePath)
        assertEquals(listOf("h1/u1/old.jpg"), repo.deletedEvidence)
    }

    @Test
    fun `no se puede editar el pago de otra persona`() {
        repo.paymentList += existing(owner = "u2", evidence = null)
        val s = vm("p1").state.value
        assertTrue(s.readOnly)
        assertEquals("Solo puedes editar tus propios pagos", s.message)
    }
}
