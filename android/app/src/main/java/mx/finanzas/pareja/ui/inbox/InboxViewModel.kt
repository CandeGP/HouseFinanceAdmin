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

data class InboxUiState(
    val items: List<Transaction> = emptyList(),
    val cards: List<Card> = emptyList(),
    val categories: List<Category> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

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
                val items = repo.pendingTransactions().filter { it.ownerId == userId }
                val cards = repo.cards().filter { it.ownerId == userId && !it.archived }
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

    fun confirm(id: String, cardId: String?, categoryId: String?) {
        if (cardId == null) {
            _state.update { it.copy(message = "Selecciona una tarjeta antes de confirmar") }
            return
        }
        run { repo.confirmTransaction(id, cardId, categoryId) }
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
