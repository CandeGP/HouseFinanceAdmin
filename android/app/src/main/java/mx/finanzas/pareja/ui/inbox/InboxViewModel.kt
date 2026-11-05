package mx.finanzas.pareja.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.userMessage
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import mx.finanzas.pareja.data.TxStatus
import mx.finanzas.pareja.domain.SUGGESTION_LOOKBACK_DAYS
import mx.finanzas.pareja.domain.bankOfRaw
import mx.finanzas.pareja.domain.soleOwnCardOfBank
import mx.finanzas.pareja.domain.suggestForMerchant

data class InboxUiState(
    val items: List<Transaction> = emptyList(),
    val cards: List<Card> = emptyList(),
    val categories: List<Category> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

/** Un cobro por aprobar con la tarjeta y categoría que se le aplicarán. */
data class ConfirmEntry(val id: String, val cardId: String?, val categoryId: String?)

/** Cobros capturados automáticamente que esperan que la persona los confirme o descarte. */
class InboxViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val cards = repo.cards().filter { it.ownerId == userId && !it.archived }
                // Pendientes sin tarjeta: se precarga la tarjeta del banco del aviso o la del último gasto en ese comercio.
                // Solo se sugiere; al aprobar es cuando se guarda.
                val history = recentConfirmedWithCard()
                val items = repo.pendingTransactions().filter { it.ownerId == userId }.map { tx ->
                    if (tx.cardId != null) tx else tx.copy(cardId = suggestedCardId(tx, cards, history))
                }
                val categories = repo.categories()
                _state.update {
                    it.copy(items = items, cards = cards, categories = categories, loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = userMessage(e)) }
            }
        }
    }

    private fun suggestedCardId(tx: Transaction, cards: List<Card>, history: List<Transaction>): String? {
        val byBank = bankOfRaw(tx.captureRaw)?.let { soleOwnCardOfBank(cards, userId, it) }
        val byMerchant = suggestForMerchant(tx.merchant, history)?.cardId?.takeIf { id -> cards.any { it.id == id } }
        return byBank?.id ?: byMerchant
    }

    /** Tus gastos confirmados de los últimos 90 días con tarjeta, lo más reciente primero; si falla, sin sugerencias. */
    private suspend fun recentConfirmedWithCard(): List<Transaction> = try {
        val today = Clock.System.todayIn(TimeZone.of("America/Mexico_City"))
        repo.transactions(today.minus(SUGGESTION_LOOKBACK_DAYS, DateTimeUnit.DAY), today)
            .filter { it.ownerId == userId && it.status == TxStatus.CONFIRMED && it.cardId != null }
            .sortedByDescending { it.occurredAt }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    fun confirm(id: String, cardId: String?, categoryId: String?) {
        if (cardId == null) {
            _state.update { it.copy(message = "Selecciona una tarjeta antes de confirmar") }
            return
        }
        run { repo.confirmTransaction(id, cardId, categoryId) }
    }

    /** Aprueba de una vez los que ya tienen tarjeta; los que no, quedan en la lista y se avisa cuántos. */
    fun confirmAll(entries: List<ConfirmEntry>) {
        val ready = entries.filter { it.cardId != null }
        val skipped = entries.size - ready.size
        if (ready.isEmpty()) {
            _state.update { it.copy(message = "Elige la tarjeta de cada cobro para poder aprobarlos") }
            return
        }
        run {
            ready.forEach { repo.confirmTransaction(it.id, it.cardId!!, it.categoryId) }
            if (skipped > 0) _state.update { it.copy(message = "$skipped sin tarjeta: elígela con Editar para aprobarlos") }
        }
    }

    fun discard(id: String) {
        val target = _state.value.items.firstOrNull { it.id == id } ?: return
        if (target.ownerId != userId) return
        run { repo.deleteTransaction(id) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun run(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }
}
