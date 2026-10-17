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
import mx.finanzas.pareja.data.MsiInstallment
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.monthBounds
import mx.finanzas.pareja.ui.dashboard.Scope
import mx.finanzas.pareja.ui.installments.INSTALLMENT_HORIZON_MONTHS

data class TransactionsUiState(
    val year: Int,
    val month: Int,
    val scope: Scope = Scope.BOTH,
    val items: List<Transaction> = emptyList(),
    val cards: List<Card> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Mensualidades de compras a meses que se pagan en el mes elegido (según el alcance). */
    val installments: List<MsiInstallment> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class TransactionsViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val today: LocalDate,
) : ViewModel() {
    private val _state = MutableStateFlow(TransactionsUiState(year = today.year, month = today.monthNumber))
    val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

    private var monthItems: List<Transaction> = emptyList()

    /** Calendario completo de mensualidades; no cambia al moverse de mes, así que se pide una sola vez por carga. */
    private var schedule: List<MsiInstallment> = emptyList()

    init {
        load()
    }

    fun load() = load(refreshSchedule = true)

    private fun load(refreshSchedule: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val (from, to) = monthBounds(_state.value.year, _state.value.month)
                monthItems = repo.transactions(from, to)
                // Sin el calendario (por ejemplo, sin conexión a esa consulta) Gastos sigue funcionando.
                if (refreshSchedule) {
                    schedule = runCatching { repo.msiSchedule(today, INSTALLMENT_HORIZON_MONTHS) }.getOrDefault(emptyList())
                }
                val cards = repo.cards()
                val categories = repo.categories()
                _state.update { it.copy(cards = cards, categories = categories, loading = false) }
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = userMessage(e)) }
            }
        }
    }

    fun setScope(scope: Scope) {
        _state.update { it.copy(scope = scope) }
        publish()
    }

    fun previousMonth() = shiftMonth(-1)
    fun nextMonth() = shiftMonth(1)

    fun delete(id: String) {
        val target = monthItems.firstOrNull { it.id == id } ?: return
        if (target.ownerId != userId) return
        viewModelScope.launch {
            try {
                repo.deleteTransaction(id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = userMessage(e)) }
            }
        }
    }

    private fun shiftMonth(delta: Int) {
        val index = _state.value.year * 12 + (_state.value.month - 1) + delta
        _state.update { it.copy(year = index / 12, month = index % 12 + 1) }
        load(refreshSchedule = false)
    }

    private fun publish() {
        val scope = _state.value.scope
        val visible = if (scope == Scope.MINE) monthItems.filter { it.ownerId == userId } else monthItems
        val myCards = _state.value.cards.filter { it.ownerId == userId }.map { it.id }.toSet()
        val installments = schedule
            .filter { it.dueDate.year == _state.value.year && it.dueDate.monthNumber == _state.value.month }
            .filter { scope == Scope.BOTH || it.cardId in myCards }
            .sortedByDescending { it.amount }
        _state.update { it.copy(items = visible, installments = installments) }
    }
}
