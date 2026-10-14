package mx.finanzas.pareja.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.NewTransaction
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TransactionPatch
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.DraftField
import mx.finanzas.pareja.domain.DraftResult
import mx.finanzas.pareja.domain.TransactionDraft
import mx.finanzas.pareja.domain.ValidDraft

data class FormUiState(
    val draft: TransactionDraft,
    val cards: List<Card> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errors: Map<DraftField, String> = emptyMap(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val readOnly: Boolean = false,
    val message: String? = null,
)

class TransactionFormViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val householdId: String,
    private val editingId: String?,
    today: LocalDate,
) : ViewModel() {
    private val _state = MutableStateFlow(
        FormUiState(
            draft = TransactionDraft(
                merchant = "", amountText = "", date = today, cardId = null, categoryId = null,
                mode = PaymentMode.CONTADO, installmentsText = "", installmentNumberText = "", notes = "",
            ),
        ),
    )
    val state: StateFlow<FormUiState> = _state.asStateFlow()

    val isEditing: Boolean get() = editingId != null

    init {
        load()
    }

    fun onDraftChange(draft: TransactionDraft) {
        _state.update { it.copy(draft = draft, errors = emptyMap()) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun save() {
        val current = _state.value
        if (current.readOnly || current.saving) return

        when (val result = current.draft.validate()) {
            is DraftResult.Invalid -> _state.update { it.copy(errors = result.errors) }
            is DraftResult.Valid -> persist(result.value)
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val cards = repo.cards().filter { it.ownerId == userId && !it.archived }
                val categories = repo.categories()
                val existing = editingId?.let { repo.transaction(it) }
                _state.update { state ->
                    val base = state.copy(cards = cards, categories = categories, loading = false)
                    when {
                        editingId != null && existing == null ->
                            base.copy(readOnly = true, message = "No se encontró el gasto")
                        existing != null && existing.ownerId != userId ->
                            base.copy(
                                draft = existing.toDraft(),
                                readOnly = true,
                                message = "Solo puedes editar tus propios gastos",
                            )
                        existing != null -> base.copy(draft = existing.toDraft())
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

    private fun persist(v: ValidDraft) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            try {
                if (editingId == null) {
                    repo.addTransaction(
                        NewTransaction(
                            householdId = householdId, ownerId = userId, cardId = v.cardId,
                            categoryId = v.categoryId, merchant = v.merchant, amount = v.amount,
                            occurredAt = v.date, paymentMode = v.mode, installments = v.installments,
                            installmentNumber = v.installmentNumber, notes = v.notes,
                        ),
                    )
                } else {
                    repo.updateTransaction(
                        editingId,
                        TransactionPatch(
                            cardId = v.cardId, categoryId = v.categoryId, merchant = v.merchant,
                            amount = v.amount, occurredAt = v.date, paymentMode = v.mode,
                            installments = v.installments, installmentNumber = v.installmentNumber,
                            notes = v.notes,
                        ),
                    )
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, message = userMessage(e)) }
            }
        }
    }
}

private fun Transaction.toDraft() = TransactionDraft(
    merchant = merchant,
    amountText = amount.toBigDecimal().stripTrailingZeros().toPlainString(),
    date = occurredAt,
    cardId = cardId,
    categoryId = categoryId,
    mode = paymentMode,
    installmentsText = installments?.toString().orEmpty(),
    installmentNumberText = installmentNumber?.toString().orEmpty(),
    notes = notes.orEmpty(),
)
