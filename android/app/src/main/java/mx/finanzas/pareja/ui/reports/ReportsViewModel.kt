package mx.finanzas.pareja.ui.reports

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
import mx.finanzas.pareja.domain.TrendRow
import mx.finanzas.pareja.domain.monthToDateRanges
import mx.finanzas.pareja.domain.spendingByCard
import mx.finanzas.pareja.domain.spendingByCategory
import mx.finanzas.pareja.domain.trendRows

data class ReportsUiState(
    val byCategory: List<TrendRow> = emptyList(),
    val byCard: List<TrendRow> = emptyList(),
    val currentTotal: Double = 0.0,
    val previousTotal: Double = 0.0,
    val currentRange: ClosedRange<LocalDate>? = null,
    val previousRange: ClosedRange<LocalDate>? = null,
    val loading: Boolean = true,
    val message: String? = null,
)

/** Lo que va del mes contra el mismo tramo del mes pasado, por categoría y por tarjeta (todo el hogar). */
class ReportsViewModel(
    private val repo: FinanceRepository,
    private val today: LocalDate,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val (now, before) = monthToDateRanges(today)
                val categories = repo.categories()
                val cards = repo.cards()
                val current = repo.transactions(now.start, now.endInclusive)
                val previous = repo.transactions(before.start, before.endInclusive)
                val byCategory = spendingByCategory(current, categories) to spendingByCategory(previous, categories)
                val byCard = spendingByCard(current, cards) to spendingByCard(previous, cards)
                _state.update {
                    it.copy(
                        byCategory = trendRows(byCategory.first, byCategory.second),
                        byCard = trendRows(byCard.first, byCard.second),
                        currentTotal = byCategory.first.sumOf { s -> s.total },
                        previousTotal = byCategory.second.sumOf { s -> s.total },
                        currentRange = now, previousRange = before,
                        loading = false, message = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = userMessage(e)) }
            }
        }
    }
}
