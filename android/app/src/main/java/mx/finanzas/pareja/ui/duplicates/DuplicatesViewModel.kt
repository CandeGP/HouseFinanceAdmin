package mx.finanzas.pareja.ui.duplicates

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
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.DuplicatePair
import mx.finanzas.pareja.domain.findDuplicateCandidates

/** Cuánto hacia atrás se buscan duplicados: lo reciente es lo que aún se puede estar capturando dos veces. */
const val DUPLICATES_LOOKBACK_DAYS = 90

data class DuplicatesUiState(
    val pairs: List<DuplicatePair> = emptyList(),
    val cards: Map<String, Card> = emptyMap(),
    val loading: Boolean = true,
    val message: String? = null,
)

/**
 * Posibles cargos duplicados con "Borrar este" (solo los propios) y "No es duplicado".
 * shortcut: los descartes se guardan por dispositivo (saveDismissed), no en la base; subir a una tabla si hace falta compartirlos.
 */
class DuplicatesViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val today: LocalDate,
    private var dismissed: Set<String>,
    private val saveDismissed: (Set<String>) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(DuplicatesUiState())
    val state: StateFlow<DuplicatesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val from = today.minus(DUPLICATES_LOOKBACK_DAYS, DateTimeUnit.DAY)
                val pairs = findDuplicateCandidates(repo.transactions(from, today)).filter { it.key !in dismissed }
                val cards = repo.cards().associateBy { it.id }
                _state.update { it.copy(pairs = pairs, cards = cards, loading = false, message = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = userMessage(e)) }
            }
        }
    }

    fun dismiss(pair: DuplicatePair) {
        dismissed = dismissed + pair.key
        saveDismissed(dismissed)
        _state.update { s -> s.copy(pairs = s.pairs.filter { it.key != pair.key }) }
    }

    /** Borra el cargo indicado (debe ser propio) y quita de la lista los pares que lo incluían. */
    fun delete(pair: DuplicatePair, victimId: String) {
        val victim = listOf(pair.first, pair.second).first { it.id == victimId }
        if (victim.ownerId != userId) return
        viewModelScope.launch {
            try {
                repo.deleteTransaction(victimId)
                _state.update { s ->
                    s.copy(pairs = s.pairs.filter { it.first.id != victimId && it.second.id != victimId }, message = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }
}
