package mx.finanzas.pareja.ui.loans

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
import mx.finanzas.pareja.data.LoanRepayment
import mx.finanzas.pareja.data.LoanRepaymentPatch
import mx.finanzas.pareja.data.NewLoan
import mx.finanzas.pareja.data.NewLoanRepayment
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.LoanDraft
import mx.finanzas.pareja.domain.LoanField
import mx.finanzas.pareja.domain.LoanRepaymentDraft
import mx.finanzas.pareja.domain.LoanRepaymentResult
import mx.finanzas.pareja.domain.LoanResult
import mx.finanzas.pareja.domain.LoanTotals
import mx.finanzas.pareja.domain.LoanView
import mx.finanzas.pareja.domain.PersonBalance
import mx.finanzas.pareja.domain.buildLoanViews
import mx.finanzas.pareja.domain.distinctPayees
import mx.finanzas.pareja.domain.loanTotals
import mx.finanzas.pareja.domain.personBalances
import mx.finanzas.pareja.ui.dashboard.Scope

data class LoansUiState(
    val scope: Scope = Scope.BOTH,
    val balances: List<PersonBalance> = emptyList(),
    val totals: LoanTotals = LoanTotals(owedToYou = 0.0, youOwe = 0.0),
    /** Personas ya usadas, para sugerirlas al registrar un préstamo nuevo. */
    val people: List<String> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
    val loanErrors: Map<LoanField, String> = emptyMap(),
    val repaymentError: String? = null,
    /** Se enciende al guardar un préstamo o abono, para cerrar el diálogo abierto. */
    val saved: Boolean = false,
)

class LoansViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val householdId: String,
    private val today: LocalDate,
) : ViewModel() {
    private val _state = MutableStateFlow(LoansUiState())
    val state: StateFlow<LoansUiState> = _state.asStateFlow()

    private var allViews: List<LoanView> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                allViews = buildLoanViews(repo.loans(), repo.loanRepayments())
                publish(loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = userMessage(e)) }
            }
        }
    }

    fun setScope(scope: Scope) {
        _state.update { it.copy(scope = scope) }
        publish(loading = _state.value.loading)
    }

    fun addLoan(draft: LoanDraft) {
        when (val result = draft.validate(today)) {
            is LoanResult.Invalid -> _state.update { it.copy(loanErrors = result.errors, saved = false) }
            is LoanResult.Valid -> write {
                repo.addLoan(
                    NewLoan(
                        householdId = householdId, ownerId = userId, person = result.person,
                        direction = result.direction, amount = result.amount, occurredOn = result.occurredOn,
                        method = result.method, note = result.note,
                    ),
                )
                _state.update { it.copy(loanErrors = emptyMap(), saved = true) }
            }
        }
    }

    fun addRepayment(view: LoanView, draft: LoanRepaymentDraft) {
        if (view.loan.ownerId != userId) return
        when (val result = draft.validate(view.remaining, today)) {
            is LoanRepaymentResult.Invalid -> _state.update { it.copy(repaymentError = result.message, saved = false) }
            is LoanRepaymentResult.Valid -> write {
                repo.addLoanRepayment(
                    NewLoanRepayment(
                        householdId = householdId, ownerId = userId, loanId = view.loan.id,
                        amount = result.amount, paidOn = result.paidOn, method = result.method, note = result.note,
                    ),
                )
                _state.update { it.copy(repaymentError = null, saved = true) }
            }
        }
    }

    /** Edita un abono propio. El tope es lo que falta del préstamo más lo que ese mismo abono ya cubría. */
    fun updateRepayment(view: LoanView, repayment: LoanRepayment, draft: LoanRepaymentDraft) {
        if (view.loan.ownerId != userId) return
        when (val result = draft.validate(view.remaining + repayment.amount, today)) {
            is LoanRepaymentResult.Invalid -> _state.update { it.copy(repaymentError = result.message, saved = false) }
            is LoanRepaymentResult.Valid -> write {
                repo.updateLoanRepayment(
                    repayment.id,
                    LoanRepaymentPatch(
                        amount = result.amount, paidOn = result.paidOn, method = result.method, note = result.note,
                    ),
                )
                _state.update { it.copy(repaymentError = null, saved = true) }
            }
        }
    }

    fun deleteLoan(id: String) {
        if (ownedLoan(id) == null) return
        write { repo.deleteLoan(id) }
    }

    fun deleteRepayment(loanId: String, repaymentId: String) {
        if (ownedLoan(loanId) == null) return
        write { repo.deleteLoanRepayment(repaymentId) }
    }

    fun setForgiven(id: String, forgiven: Boolean) {
        if (ownedLoan(id) == null) return
        write { repo.setLoanForgiven(id, forgiven) }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false, loanErrors = emptyMap(), repaymentError = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun ownedLoan(id: String): LoanView? =
        allViews.firstOrNull { it.loan.id == id && it.loan.ownerId == userId }

    /** Ejecuta una escritura y recarga; cualquier fallo se muestra sin romper la pantalla. */
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    private fun publish(loading: Boolean) {
        val visible = if (_state.value.scope == Scope.MINE) allViews.filter { it.loan.ownerId == userId } else allViews
        _state.update {
            it.copy(
                loading = loading,
                balances = personBalances(visible),
                totals = loanTotals(visible),
                people = distinctPayees(allViews.map { view -> view.loan.person }),
            )
        }
    }
}
