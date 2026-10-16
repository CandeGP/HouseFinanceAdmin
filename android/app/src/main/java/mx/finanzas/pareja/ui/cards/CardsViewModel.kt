package mx.finanzas.pareja.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.CardEditPatch
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.MonthSpend
import mx.finanzas.pareja.data.NewCard
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.CardDraft
import mx.finanzas.pareja.domain.CardDraftResult
import mx.finanzas.pareja.domain.CardEditDraft
import mx.finanzas.pareja.domain.CardEditResult
import mx.finanzas.pareja.domain.CardField

data class CardsUiState(
    val cards: List<Card> = emptyList(),
    /** Tarjetas archivadas: ocultas del resto de la app pero con sus gastos intactos. */
    val archived: List<Card> = emptyList(),
    /** Gasto del mes por id de tarjeta; solo para las que tienen presupuesto. */
    val spend: Map<String, MonthSpend> = emptyMap(),
    val loading: Boolean = true,
    val errors: Map<CardField, String> = emptyMap(),
    val message: String? = null,
    val added: Boolean = false,
    /** Errores del formulario de edición y señal de que se guardó (para cerrar el diálogo). */
    val editErrors: Map<CardField, String> = emptyMap(),
    val edited: Boolean = false,
)

class CardsViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val householdId: String,
    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
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
                val spend = active.filter { it.monthlyBudget != null }
                    .mapNotNull { card -> repo.cardMonthSpend(card.id, today)?.let { card.id to it } }
                    .toMap()
                _state.update { it.copy(cards = active, archived = archived, spend = spend, loading = false) }
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

    /** Valida y guarda la edición completa de una tarjeta propia (datos, presupuesto y color). */
    fun saveEdit(id: String, draft: CardEditDraft) {
        when (val result = draft.validate()) {
            is CardEditResult.Invalid -> _state.update { it.copy(editErrors = result.errors, edited = false) }
            is CardEditResult.Valid -> viewModelScope.launch {
                val v = result.value
                try {
                    repo.editCard(
                        id,
                        CardEditPatch(
                            name = v.card.name, bank = v.card.bank, last4 = v.card.last4,
                            creditLimit = v.card.creditLimit, cutoffDay = v.card.cutoffDay, dueDay = v.card.dueDay,
                            monthlyBudget = v.monthlyBudget, color = v.color,
                        ),
                    )
                    _state.update { it.copy(editErrors = emptyMap(), edited = true) }
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(message = userMessage(e)) }
                }
            }
        }
    }

    fun consumeEdited() = _state.update { it.copy(edited = false, editErrors = emptyMap()) }

    /** `amount` null quita el presupuesto. Un monto cero o negativo se rechaza antes de llegar al servidor. */
    fun setBudget(id: String, amount: Double?) {
        if (amount != null && amount <= 0) {
            _state.update { it.copy(message = "El presupuesto debe ser mayor a cero") }
            return
        }
        viewModelScope.launch {
            try {
                repo.setCardBudget(id, amount)
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
