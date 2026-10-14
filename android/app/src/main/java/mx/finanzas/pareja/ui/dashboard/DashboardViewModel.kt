package mx.finanzas.pareja.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.CardCycleView
import mx.finanzas.pareja.domain.DashboardSummary
import mx.finanzas.pareja.domain.buildDashboard

/** Qué datos se ven: solo los míos o los de ambos (vista compartida, solo lectura). */
enum class Scope { MINE, BOTH }

data class DashboardUiState(
    val loading: Boolean = true,
    val scope: Scope = Scope.BOTH,
    val summary: DashboardSummary = buildDashboard(emptyList()),
    val error: String? = null,
    /** Cobros capturados automáticamente que esperan revisión. */
    val pendingCount: Int = 0,
)

class DashboardViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val today: () -> LocalDate,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var allViews: List<CardCycleView> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val ref = today()
                allViews = repo.cards()
                    .filterNot { it.archived }
                    .mapNotNull { card -> repo.cycleEstimate(card.id, ref)?.let { CardCycleView(card, it) } }
                val pending = runCatching { repo.pendingTransactions().count { it.ownerId == userId } }
                    .getOrDefault(0)
                _state.update { it.copy(pendingCount = pending) }
                publish(loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = userMessage(e)) }
            }
        }
    }

    fun setScope(scope: Scope) {
        _state.update { it.copy(scope = scope) }
        publish(loading = _state.value.loading)
    }

    private fun publish(loading: Boolean) {
        val scope = _state.value.scope
        val visible = if (scope == Scope.MINE) allViews.filter { it.card.ownerId == userId } else allViews
        _state.update { it.copy(loading = loading, summary = buildDashboard(visible)) }
    }
}
