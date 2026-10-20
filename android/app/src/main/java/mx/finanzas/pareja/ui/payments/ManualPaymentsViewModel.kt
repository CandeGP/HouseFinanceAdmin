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
import kotlinx.datetime.atStartOfDayIn
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.ManualPayment
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.monthBounds
import mx.finanzas.pareja.ui.dashboard.Scope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

data class ManualPaymentsUiState(
    val year: Int,
    val month: Int,
    val scope: Scope = Scope.MINE,
    val items: List<ManualPayment> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class ManualPaymentsViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    today: LocalDate,
    private val zone: TimeZone,
) : ViewModel() {
    private val _state = MutableStateFlow(ManualPaymentsUiState(year = today.year, month = today.monthNumber))
    val state: StateFlow<ManualPaymentsUiState> = _state.asStateFlow()

    private var monthItems: List<ManualPayment> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val (first, last) = monthBounds(_state.value.year, _state.value.month)
                val from = first.atStartOfDayIn(zone)
                val toExclusive = last.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
                monthItems = repo.manualPayments(from, toExclusive)
                _state.update { it.copy(loading = false) }
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

    /** Elimina un pago propio y, si tenía, su evidencia. */
    fun delete(id: String) {
        val target = monthItems.firstOrNull { it.id == id } ?: return
        if (target.ownerId != userId) return
        viewModelScope.launch {
            try {
                repo.deleteManualPayment(id)
                target.evidencePath?.let { repo.deleteEvidence(it) }
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
        load()
    }

    private fun publish() {
        val visible = if (_state.value.scope == Scope.MINE) monthItems.filter { it.ownerId == userId } else monthItems
        _state.update { it.copy(items = visible) }
    }
}
