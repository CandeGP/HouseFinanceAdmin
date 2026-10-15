package mx.finanzas.pareja.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.NewCard
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.CardDraft
import mx.finanzas.pareja.domain.CardDraftResult
import mx.finanzas.pareja.domain.CardField

data class CardsUiState(
    val cards: List<Card> = emptyList(),
    /** Tarjetas archivadas: ocultas del resto de la app pero con sus gastos intactos. */
    val archived: List<Card> = emptyList(),
    val loading: Boolean = true,
    val errors: Map<CardField, String> = emptyMap(),
    val message: String? = null,
    val added: Boolean = false,
)

class CardsViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val householdId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(CardsUiState())
    val state: StateFlow<CardsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val (archived, active) = repo.cards().partition { it.archived }
                _state.update { it.copy(cards = active, archived = archived, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = userMessage(e)) }
            }
        }
    }

    fun addCard(draft: CardDraft) {
        when (val result = draft.validate()) {
            is CardDraftResult.Invalid -> _state.update { it.copy(errors = result.errors, added = false) }
            is CardDraftResult.Valid -> viewModelScope.launch {
                val v = result.value
                try {
                    repo.addCard(
                        NewCard(
                            householdId = householdId, ownerId = userId, name = v.name, bank = v.bank,
                            last4 = v.last4, creditLimit = v.creditLimit, cutoffDay = v.cutoffDay, dueDay = v.dueDay,
                        ),
                    )
                    _state.update { it.copy(errors = emptyMap(), added = true) }
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(message = userMessage(e)) }
                }
            }
        }
    }

    fun archive(id: String) {
        viewModelScope.launch {
            try {
                repo.archiveCard(id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    fun restore(id: String) {
        viewModelScope.launch {
            try {
                repo.restoreCard(id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    fun consumeAdded() = _state.update { it.copy(added = false, errors = emptyMap()) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
}
