package mx.finanzas.pareja.ui.budgets

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
import mx.finanzas.pareja.domain.CategoryBudgetRow
import mx.finanzas.pareja.domain.categoryBudgetRows
import mx.finanzas.pareja.domain.parseAmount

data class CategoryBudgetsUiState(
    val rows: List<CategoryBudgetRow> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
    /** Error del campo de monto del diálogo abierto. */
    val error: String? = null,
    /** Se enciende al guardar, para cerrar el diálogo. */
    val saved: Boolean = false,
)

class CategoryBudgetsViewModel(
    private val repo: FinanceRepository,
    private val today: LocalDate,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoryBudgetsUiState())
    val state: StateFlow<CategoryBudgetsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val rows = categoryBudgetRows(repo.categories(), repo.categoryMonthSpend(today))
                _state.update { it.copy(rows = rows, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = userMessage(e)) }
            }
        }
    }

    /** [text] vacío quita el presupuesto; cero, negativo o texto que no es un monto se rechaza. */
    fun setBudget(categoryId: String, text: String) {
        val clean = text.trim()
        val amount = if (clean.isEmpty()) null else parseAmount(clean)
        if (clean.isNotEmpty() && amount == null) {
            _state.update { it.copy(error = "Presupuesto inválido (déjalo vacío para quitarlo)", saved = false) }
            return
        }
        viewModelScope.launch {
            try {
                repo.setCategoryBudget(categoryId, amount)
                _state.update { it.copy(error = null, saved = true) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false, error = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
}
