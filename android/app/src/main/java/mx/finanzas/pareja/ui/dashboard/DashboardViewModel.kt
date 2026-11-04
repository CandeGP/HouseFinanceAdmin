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
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.CardCycleView
import mx.finanzas.pareja.domain.DashboardSummary
import mx.finanzas.pareja.domain.NO_QUINCENAS
import mx.finanzas.pareja.domain.NextPayment
import mx.finanzas.pareja.domain.QuincenaTotals
import mx.finanzas.pareja.domain.nextPayment
import mx.finanzas.pareja.domain.paymentObligations
import mx.finanzas.pareja.domain.quincenaTotals
import mx.finanzas.pareja.domain.RECURRING_LOOKBACK_MONTHS
import mx.finanzas.pareja.domain.RecurringCharge
import mx.finanzas.pareja.domain.SpendingSlice
import mx.finanzas.pareja.domain.detectRecurring
import mx.finanzas.pareja.domain.spendingByCategory
import mx.finanzas.pareja.domain.buildDashboard
import mx.finanzas.pareja.domain.LoanTotals
import mx.finanzas.pareja.domain.buildLoanViews
import mx.finanzas.pareja.domain.loanTotals

/** Qué datos se ven: solo los míos o los de ambos (vista compartida, solo lectura). */
enum class Scope { MINE, BOTH }

data class DashboardUiState(
    val loading: Boolean = true,
    val scope: Scope = Scope.MINE,
    val summary: DashboardSummary = buildDashboard(emptyList()),
    val error: String? = null,
    /** Cobros capturados automáticamente que esperan revisión. */
    val pendingCount: Int = 0,
    /** Cómo va el pago del último ciclo cerrado de cada tarjeta (por id). */
    val statuses: Map<String, CardCycleStatus> = emptyMap(),
    /** Cargos que se repiten cada mes y se esperan pronto (suscripciones, servicios, renta). */
    val recurring: List<RecurringCharge> = emptyList(),
    /** Lo que toca pagar de cada tarjeta (por id) y cuándo. */
    val nextPayments: Map<String, NextPayment> = emptyMap(),
    /** Lo que vence este mes, separado por quincena de la fecha de pago. */
    val quincenas: QuincenaTotals = NO_QUINCENAS,
    /** Gasto del mes en curso por categoría, de mayor a menor (para la dona del Resumen). */
    val monthSpend: List<SpendingSlice> = emptyList(),
    /** Lo que te deben y lo que debes en préstamos y cobros divididos (solo los tuyos). */
    val loanTotals: LoanTotals = LoanTotals(owedToYou = 0.0, youOwe = 0.0),
    /** La quincena en curso es la 1ª (días 1 al 15); si no, la 2ª. Se marca en amarillo en Resumen. */
    val firstQuincenaActive: Boolean = true,
)

/** Último día de la 1ª quincena; del siguiente en adelante cuenta la 2ª. */
private const val FIRST_QUINCENA_LAST_DAY = 15

class DashboardViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val today: () -> LocalDate,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var allViews: List<CardCycleView> = emptyList()
    private var allTransactions: List<Transaction> = emptyList()
    private var allCategories: List<Category> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val ref = today()
                val cards = repo.cards().filterNot { it.archived }
                // Una sola consulta para todas las tarjetas (estimación del ciclo y estado de pago).
                val snapshots = repo.cardSnapshots(cards.map { it.id }, ref).associateBy { it.cardId }
                allViews = cards.mapNotNull { card -> snapshots[card.id]?.estimate?.let { CardCycleView(card, it) } }
                val pending = runCatching { repo.pendingTransactions().count { it.ownerId == userId } }
                    .getOrDefault(0)
                // Un mes más que la ventana de detección, para no perder el primer cargo del límite.
                allTransactions = repo.transactions(ref.minus(RECURRING_LOOKBACK_MONTHS + 1, DateTimeUnit.MONTH), ref)
                // Solo pone nombre a la dona: si falla, las categorías salen como «Sin categoría».
                allCategories = runCatching { repo.categories() }.getOrDefault(emptyList())
                val statuses = allViews.mapNotNull { view -> snapshots[view.card.id]?.status?.let { view.card.id to it } }.toMap()
                // Opcional: si Préstamos falla, Resumen sigue funcionando sin esa tarjeta.
                val loans = runCatching {
                    val mine = repo.loans().filter { it.ownerId == userId }
                    loanTotals(buildLoanViews(mine, repo.loanRepayments().filter { r -> mine.any { it.id == r.loanId } }))
                }.getOrDefault(LoanTotals(owedToYou = 0.0, youOwe = 0.0))
                _state.update { it.copy(pendingCount = pending, statuses = statuses, loanTotals = loans) }
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
        val now = today()
        val thisMonth = visibleTransactions.filter {
            it.occurredAt.year == now.year && it.occurredAt.monthNumber == now.monthNumber
        }
        val payments = visible.associate { view ->
            view.card.id to nextPayment(view, _state.value.statuses[view.card.id], now)
        }
        _state.update {
            it.copy(
                loading = loading,
                summary = buildDashboard(visible),
                recurring = detectRecurring(visibleTransactions, now),
                nextPayments = payments,
                monthSpend = spendingByCategory(thisMonth, allCategories),
                firstQuincenaActive = now.dayOfMonth <= FIRST_QUINCENA_LAST_DAY,
                quincenas = quincenaTotals(
                    visible.flatMap { view -> paymentObligations(view, _state.value.statuses[view.card.id], now) },
                    now,
                ),
            )
        }
    }
}
