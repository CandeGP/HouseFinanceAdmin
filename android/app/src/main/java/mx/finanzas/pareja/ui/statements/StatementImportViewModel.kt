package mx.finanzas.pareja.ui.statements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.ImportInstallment
import mx.finanzas.pareja.data.ImportMovement
import mx.finanzas.pareja.data.ImportResult
import mx.finanzas.pareja.data.StatementImportSummary
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.ParsedStatement
import mx.finanzas.pareja.domain.Reconciliation
import mx.finanzas.pareja.domain.evidencePath
import mx.finanzas.pareja.domain.parseStatement
import mx.finanzas.pareja.domain.reconcile

enum class ImportStep { PICK, REVIEW, DONE }

data class StatementImportUiState(
    val step: ImportStep = ImportStep.PICK,
    val cards: List<Card> = emptyList(),
    val parsed: ParsedStatement? = null,
    val cardId: String? = null,
    val reconciliation: Reconciliation? = null,
    /** Posiciones (dentro de `missingInApp`) que la persona desmarcó y no se importarán. */
    val excluded: Set<Int> = emptySet(),
    val busy: Boolean = false,
    val message: String? = null,
    val result: ImportResult? = null,
)

private const val WINDOW_DAYS = 7
private const val FALLBACK_PERIOD_DAYS = 31

/** Lee el texto de un estado de cuenta, lo concilia con lo registrado e importa lo que falta. */
class StatementImportViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val householdId: String,
    private val newFileId: () -> String,
) : ViewModel() {
    private val _state = MutableStateFlow(StatementImportUiState())
    val state: StateFlow<StatementImportUiState> = _state.asStateFlow()

    private var pdfBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            try {
                val cards = repo.cards().filter { it.ownerId == userId && !it.archived }
                _state.update { it.copy(cards = cards) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    /** `text` es el texto extraído del PDF; `bytes` el archivo original (opcional, se guarda como evidencia). */
    fun onPdfRead(text: String, bytes: ByteArray?) {
        if (text.isBlank()) {
            _state.update {
                it.copy(message = "No pude leer texto en este PDF: parece una imagen escaneada. Por ahora solo se leen estados de cuenta digitales.")
            }
            return
        }
        val parsed = parseStatement(text)
        if (parsed == null) {
            _state.update { it.copy(message = "Este PDF no parece un estado de cuenta compatible (no encontré la fecha de corte).") }
            return
        }
        pdfBytes = bytes

        val last4 = parsed.summary.cardLast4
        val guess = last4?.let { l4 ->
            _state.value.cards.firstOrNull { it.last4 == l4 || l4 in it.altLast4 }
        }
        _state.update {
            it.copy(
                step = ImportStep.REVIEW, parsed = parsed, cardId = guess?.id,
                reconciliation = null, excluded = emptySet(), message = null, result = null,
            )
        }
        guess?.let { recompute(it.id) }
    }

    fun selectCard(cardId: String) {
        _state.update { it.copy(cardId = cardId, excluded = emptySet()) }
        recompute(cardId)
    }

    fun toggleMovement(index: Int) {
        _state.update {
            it.copy(excluded = if (index in it.excluded) it.excluded - index else it.excluded + index)
        }
    }

    fun reset() {
        pdfBytes = null
        _state.update { StatementImportUiState(cards = it.cards) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun reportUnreadable() {
        _state.update { it.copy(message = "No se pudo abrir el archivo PDF") }
    }

    private fun recompute(cardId: String) {
        val parsed = _state.value.parsed ?: return
        viewModelScope.launch {
            try {
                val end = parsed.summary.periodEnd ?: parsed.summary.cutoffDate
                val start = parsed.summary.periodStart ?: end.minus(FALLBACK_PERIOD_DAYS, DateTimeUnit.DAY)
                val txs = repo.transactions(
                    start.minus(WINDOW_DAYS, DateTimeUnit.DAY),
                    end.plus(WINDOW_DAYS, DateTimeUnit.DAY),
                )
                val result = reconcile(parsed, txs, cardId)
                _state.update { it.copy(reconciliation = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    fun import() {
        val current = _state.value
        val parsed = current.parsed ?: return
        val cardId = current.cardId
        val rec = current.reconciliation
        if (cardId == null || rec == null) {
            _state.update { it.copy(message = "Selecciona a qué tarjeta pertenece este estado de cuenta") }
            return
        }
        if (current.busy) return

        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            var uploadedPath: String? = null
            try {
                val bytes = pdfBytes
                if (bytes != null) {
                    val path = requireNotNull(
                        evidencePath(householdId, userId, "statement-${newFileId()}", "application/pdf"),
                    )
                    repo.uploadEvidence(path, bytes, "application/pdf")
                    uploadedPath = path
                }

                val s = parsed.summary
                val result = repo.importStatement(
                    cardId = cardId,
                    summary = StatementImportSummary(
                        bank = s.bank, cardLast4 = s.cardLast4, periodStart = s.periodStart,
                        periodEnd = s.periodEnd, cutoffDate = s.cutoffDate, dueDate = s.dueDate,
                        creditLimit = s.creditLimit, payNoInterest = s.payNoInterest,
                        minimumPayment = s.minimumPayment, filePath = uploadedPath,
                    ),
                    movements = rec.missingInApp
                        .filterIndexed { index, _ -> index !in current.excluded }
                        .map { ImportMovement(it.operationDate, it.description, it.amount) },
                    installments = rec.missingInstallments.map {
                        ImportInstallment(it.purchaseDate, it.description, it.originalAmount, it.current, it.total)
                    },
                )
                _state.update { it.copy(busy = false, step = ImportStep.DONE, result = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uploadedPath?.let { path ->
                    try { repo.deleteEvidence(path) } catch (_: Exception) { /* mejor esfuerzo */ }
                }
                _state.update { it.copy(busy = false, message = userMessage(e)) }
            }
        }
    }
}
