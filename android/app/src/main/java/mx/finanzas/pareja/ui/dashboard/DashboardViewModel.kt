package mx.finanzas.pareja.ui.dashboard

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
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.cycleStatuses
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.CardCycleView
import mx.finanzas.pareja.domain.DashboardSummary
import mx.finanzas.pareja.domain.RECURRING_LOOKBACK_MONTHS
import mx.finanzas.pareja.domain.RecurringCharge
import mx.finanzas.pareja.domain.detectRecurring
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
    /** Cómo va el pago del último ciclo cerrado de cada tarjeta (por id). */
    val statuses: Map<String, CardCycleStatus> = emptyMap(),
    /** Cargos que se repiten cada mes y se esperan pronto (suscripciones, servicios, renta). */
    val recurring: List<RecurringCharge> = emptyList(),
)

class DashboardViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val today: () -> LocalDate,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var allViews: List<CardCycleView> = emptyList()
    private var allTransactions: List<Transaction> = emptyList()

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
                // Un mes más que la ventana de detección, para no perder el primer cargo del límite.
                allTransactions = repo.transactions(ref.minus(RECURRING_LOOKBACK_MONTHS + 1, DateTimeUnit.MONTH), ref)
                val statuses = repo.cycleStatuses(allViews.map { it.card.id }, ref)
                _state.update { it.copy(pendingCount = pending, statuses = statuses) }
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
        val visibleTransactions =
            if (scope == Scope.MINE) allTransactions.filter { it.ownerId == userId } else allTransactions
        _state.update {
            it.copy(
                loading = loading,
                summary = buildDashboard(visible),
                recurring = detectRecurring(visibleTransactions, today()),
            )
        }
    }
}
