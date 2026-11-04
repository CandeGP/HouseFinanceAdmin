package mx.finanzas.pareja.ui.transactions

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
import mx.finanzas.pareja.domain.SUGGESTION_LOOKBACK_DAYS
import mx.finanzas.pareja.domain.TOP_SUGGESTIONS
import mx.finanzas.pareja.domain.cardLabel
import mx.finanzas.pareja.domain.mostUsedIds
import mx.finanzas.pareja.domain.suggestForMerchant
import mx.finanzas.pareja.domain.suggestedCategoryIds
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.NewTransaction
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TransactionPatch
import mx.finanzas.pareja.data.userMessage
import java.util.Locale
import mx.finanzas.pareja.domain.DraftField
import mx.finanzas.pareja.domain.DraftResult
import mx.finanzas.pareja.domain.ReceiptGuess
import mx.finanzas.pareja.domain.TransactionDraft
import mx.finanzas.pareja.domain.ValidDraft
import mx.finanzas.pareja.domain.formatDateEs
import mx.finanzas.pareja.domain.formatMxn

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
    /** Resumen de lo que se leyó de una imagen cargada (para que la persona lo revise). */
    val receiptNote: String? = null,
    /** Lo que más usas (últimos 90 días): salen de entrada; lo demás va detrás de «Ver todas» / «+N». */
    val topCategoryIds: List<String> = emptyList(),
    val topCardIds: List<String> = emptyList(),
    /** Aviso de lo que se completó solo a partir del último gasto en el mismo comercio. */
    val suggestionNote: String? = null,
)

class TransactionFormViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val householdId: String,
    private val editingId: String?,
    val today: LocalDate,
) : ViewModel() {
    /** Tus gastos recientes (lo más nuevo primero), para sugerir categoría y tarjeta por comercio. */
    private var history: List<Transaction> = emptyList()

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
        _state.update { s ->
            val merchantChanged = draft.merchant != s.draft.merchant
            val hit = if (merchantChanged && !isEditing) suggestForMerchant(draft.merchant, history) else null
            // Solo completa lo que está vacío: nunca pisa lo que ya elegiste.
            val category = hit?.categoryId?.takeIf { draft.categoryId == null }?.let { id -> s.categories.firstOrNull { it.id == id } }
            val card = hit?.cardId?.takeIf { draft.cardId == null }?.let { id -> s.cards.firstOrNull { it.id == id && !it.frozen } }
            val filled = draft.copy(categoryId = category?.id ?: draft.categoryId, cardId = card?.id ?: draft.cardId)
            val note = when {
                category != null || card != null -> {
                    val parts = listOfNotNull(category?.name?.let { "categoría $it" }, card?.let { "tarjeta ${cardLabel(it)}" })
                    "Completé ${parts.joinToString(" y ")} como en tu último gasto en ${hit?.merchant?.trim()}. Cámbialas si no aplica."
                }
                merchantChanged -> null
                else -> s.suggestionNote
            }
            s.copy(draft = filled, errors = emptyMap(), suggestionNote = note)
        }
    }

    /**
     * Autorrellena el formulario con lo leído de una imagen. Solo pisa los campos que se detectaron;
     * la tarjeta se busca por sus últimos 4 (o un alterno) entre las tuyas.
     */
    fun applyReceipt(guess: ReceiptGuess) {
        _state.update { s ->
            val card = guess.cardLast4?.let { l4 -> s.cards.firstOrNull { it.last4 == l4 || l4 in it.altLast4 } }
            val d = s.draft
            s.copy(
                draft = d.copy(
                    merchant = guess.merchant ?: d.merchant,
                    amountText = guess.amount?.let { "%.2f".format(Locale.US, it) } ?: d.amountText,
                    date = guess.date ?: d.date,
                    cardId = card?.id ?: d.cardId,
                    mode = if (guess.installments != null) PaymentMode.MSI else d.mode,
                    installmentsText = guess.installments?.toString() ?: d.installmentsText,
                ),
                errors = emptyMap(),
                receiptNote = receiptNote(guess, card),
            )
        }
    }

    private fun receiptNote(guess: ReceiptGuess, card: Card?): String {
        val found = listOfNotNull(
            guess.merchant?.let { "comercio $it" },
            guess.amount?.let { "monto ${formatMxn(it)}" },
            card?.let { "tarjeta ${it.name} ••${it.last4}" }
                ?: guess.cardLast4?.let { "tarjeta ••$it (no la tienes registrada)" },
            guess.installments?.let { "$it meses" },
            guess.date?.let { "fecha ${formatDateEs(it)}" },
        )
        return if (found.isEmpty()) {
            "No pude leer datos en la imagen. Llena el gasto a mano."
        } else {
            "Leí: ${found.joinToString(" · ")}. Revisa antes de guardar."
        }
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
                val recent = recentOwnTransactions()
                history = recent
                val usableIds = cards.filter { !it.frozen }.map { it.id } // las congeladas no se ofrecen de entrada
                val topCards = mostUsedIds(recent.map { it.cardId }).filter { it in usableIds }
                    .let { used -> (used + usableIds).distinct().take(TOP_SUGGESTIONS) }
                val topCategories = suggestedCategoryIds(recent.map { it.categoryId }, categories.map { it.id to it.icon })
                _state.update { state ->
                    val base = state.copy(
                        cards = cards, categories = categories, loading = false,
                        topCardIds = topCards, topCategoryIds = topCategories,
                    )
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

    /** Tus gastos de los últimos 90 días, lo más reciente primero. Si falla, el formulario sigue sin sugerencias. */
    private suspend fun recentOwnTransactions(): List<Transaction> = try {
        repo.transactions(today.minus(SUGGESTION_LOOKBACK_DAYS, DateTimeUnit.DAY), today)
            .filter { it.ownerId == userId }
            .sortedByDescending { it.occurredAt }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    /** Muestra un aviso en el formulario (por ejemplo, si el teléfono no tiene cámara). */
    fun showMessage(text: String) {
        _state.update { it.copy(message = text) }
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
