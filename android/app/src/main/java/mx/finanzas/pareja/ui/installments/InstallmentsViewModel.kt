package mx.finanzas.pareja.ui.installments

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
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.MsiInstallment
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.MsiMonth
import mx.finanzas.pareja.domain.projectInstallments
import mx.finanzas.pareja.ui.dashboard.Scope

/** Cuántos ciclos hacia adelante se proyectan: 5 años cubren cualquier plan a meses (los hay hasta de 48). */
const val INSTALLMENT_HORIZON_MONTHS = 60

data class InstallmentsUiState(
    val months: List<MsiMonth> = emptyList(),
    val cards: Map<String, Card> = emptyMap(),
    val scope: Scope = Scope.BOTH,
    val loading: Boolean = true,
    val message: String? = null,
)

/** Mensualidades de compras a meses que faltan, mes por mes, de las tarjetas del hogar (o solo las mías). */
class InstallmentsViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val today: LocalDate,
) : ViewModel() {
    private val _state = MutableStateFlow(InstallmentsUiState())
    val state: StateFlow<InstallmentsUiState> = _state.asStateFlow()

    private var all: List<MsiInstallment> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                all = repo.msiSchedule(today, INSTALLMENT_HORIZON_MONTHS)
                val cards = repo.cards().associateBy { it.id }
                _state.update { it.copy(cards = cards, loading = false, message = null) }
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = userMessage(e)) }
            }
        }
    }

    fun setScope(scope: Scope) {
        _state.update { it.copy(scope = scope) }
        publish()
    }

    private fun publish() {
        _state.update { s ->
            val visible = if (s.scope == Scope.MINE) all.filter { s.cards[it.cardId]?.ownerId == userId } else all
            s.copy(months = projectInstallments(visible))
        }
    }
}
