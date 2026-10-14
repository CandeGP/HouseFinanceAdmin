package mx.finanzas.pareja.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.ManualPayment
import mx.finanzas.pareja.data.ManualPaymentPatch
import mx.finanzas.pareja.data.NewManualPayment
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.ManualPaymentDraft
import mx.finanzas.pareja.domain.PaymentDraftResult
import mx.finanzas.pareja.domain.PaymentField
import mx.finanzas.pareja.domain.ValidManualPayment
import mx.finanzas.pareja.domain.evidencePath
import mx.finanzas.pareja.domain.localDate
import mx.finanzas.pareja.domain.timeText

/** Archivo elegido por la persona y aún no subido. */
class PendingEvidence(val bytes: ByteArray, val mimeType: String)

data class PaymentFormUiState(
    val draft: ManualPaymentDraft,
    val categories: List<Category> = emptyList(),
    val errors: Map<PaymentField, String> = emptyMap(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val readOnly: Boolean = false,
    val message: String? = null,
    val existingEvidencePath: String? = null,
    val evidenceUrl: String? = null,
    val evidenceRemoved: Boolean = false,
    val pending: PendingEvidence? = null,
) {
    val hasEvidence: Boolean
        get() = pending != null || (existingEvidencePath != null && !evidenceRemoved)
}

private const val MAX_EVIDENCE_BYTES = 10 * 1024 * 1024 // mismo límite que el bucket
private const val UNSUPPORTED_EVIDENCE ="Formato de evidencia no soportado (usa JPG, PNG, WEBP o PDF)"

class ManualPaymentFormViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val householdId: String,
    private val editingId: String?,
    today: LocalDate,
    private val zone: TimeZone,
    private val newFileId: () -> String,
    initialTime: String = "",
) : ViewModel() {
    private val _state = MutableStateFlow(
        PaymentFormUiState(draft = ManualPaymentDraft(date = today, timeText = initialTime)),
    )
    val state: StateFlow<PaymentFormUiState> = _state.asStateFlow()

    val isEditing: Boolean get() = editingId != null

    init {
        load()
    }

    fun onDraftChange(draft: ManualPaymentDraft) {
        _state.update { it.copy(draft = draft, errors = emptyMap()) }
    }

    fun reportMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun attachEvidence(bytes: ByteArray, mimeType: String) {
        if (evidencePath(householdId, userId, "x", mimeType) == null) {
            reportMessage(UNSUPPORTED_EVIDENCE)
            return
        }
        if (bytes.size > MAX_EVIDENCE_BYTES) {
            reportMessage("El archivo supera 10 MB")
            return
        }
        _state.update { it.copy(pending = PendingEvidence(bytes, mimeType), evidenceRemoved = false, message = null) }
    }

    fun removeEvidence() {
        _state.update { it.copy(pending = null, evidenceRemoved = true) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun save() {
        val current = _state.value
        if (current.readOnly || current.saving) return
        when (val result = current.draft.validate(zone)) {
            is PaymentDraftResult.Invalid -> _state.update { it.copy(errors = result.errors) }
            is PaymentDraftResult.Valid -> persist(result.value)
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val categories = repo.categories()
                val existing = editingId?.let { repo.manualPayment(it) }
                val evidenceUrl = existing?.evidencePath?.let { repo.evidenceUrl(it) }
                _state.update { state ->
                    val base = state.copy(categories = categories, loading = false)
                    when {
                        editingId != null && existing == null ->
                            base.copy(readOnly = true, message = "No se encontró el pago")
                        existing != null -> base.copy(
                            draft = existing.toDraft(zone),
                            existingEvidencePath = existing.evidencePath,
                            evidenceUrl = evidenceUrl,
                            readOnly = existing.ownerId != userId,
                            message = if (existing.ownerId != userId) "Solo puedes editar tus propios pagos" else null,
                        )
                        else -> base
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = userMessage(e)) }
            }
        }
    }

    private fun persist(v: ValidManualPayment) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            val snapshot = _state.value
            val previousPath = snapshot.existingEvidencePath
            var uploadedPath: String? = null
            try {
                var finalPath: String? = if (snapshot.evidenceRemoved) null else previousPath
                snapshot.pending?.let { pending ->
                    val path = requireNotNull(evidencePath(householdId, userId, newFileId(), pending.mimeType))
                    repo.uploadEvidence(path, pending.bytes, pending.mimeType)
                    uploadedPath = path
                    finalPath = path
                }

                if (editingId == null) {
                    repo.addManualPayment(
                        NewManualPayment(
                            householdId = householdId, ownerId = userId, method = v.method, amount = v.amount,
                            paidAt = v.paidAt, concept = v.concept, counterparty = v.counterparty,
                            reference = v.reference, categoryId = v.categoryId, notes = v.notes,
                            evidencePath = finalPath,
                        ),
                    )
                } else {
                    repo.updateManualPayment(
                        editingId,
                        ManualPaymentPatch(
                            method = v.method, amount = v.amount, paidAt = v.paidAt, concept = v.concept,
                            counterparty = v.counterparty, reference = v.reference, categoryId = v.categoryId,
                            notes = v.notes, evidencePath = finalPath,
                        ),
                    )
                }

                // Solo después de guardar con éxito se descarta la evidencia anterior reemplazada o quitada.
                if (previousPath != null && previousPath != finalPath) {
                    try { repo.deleteEvidence(previousPath) } catch (_: Exception) { /* huérfano inocuo */ }
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uploadedPath?.let { path ->
                    try { repo.deleteEvidence(path) } catch (_: Exception) { /* mejor esfuerzo */ }
                }
                _state.update { it.copy(saving = false, message = userMessage(e)) }
            }
        }
    }
}

private fun ManualPayment.toDraft(zone: TimeZone) = ManualPaymentDraft(
    method = method,
    amountText = amount.toBigDecimal().stripTrailingZeros().toPlainString(),
    date = localDate(paidAt, zone),
    timeText = timeText(paidAt, zone),
    concept = concept,
    counterparty = counterparty.orEmpty(),
    reference = reference.orEmpty(),
    categoryId = categoryId,
    notes = notes.orEmpty(),
)
