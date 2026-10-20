package mx.finanzas.pareja.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardEditPatch
import mx.finanzas.pareja.data.CardPayment
import mx.finanzas.pareja.data.CardPaymentPatch
import mx.finanzas.pareja.data.FinanceRepository
import mx.finanzas.pareja.data.MonthSpend
import mx.finanzas.pareja.data.NewCard
import mx.finanzas.pareja.data.NewCardPayment
import mx.finanzas.pareja.data.CardBalanceCheck
import mx.finanzas.pareja.data.NewCardBalanceCheck
import mx.finanzas.pareja.data.userMessage
import mx.finanzas.pareja.domain.debtTotal
import mx.finanzas.pareja.domain.parseBankBalance
import mx.finanzas.pareja.domain.CardDraft
import mx.finanzas.pareja.domain.CardPaymentDraft
import mx.finanzas.pareja.domain.CardPaymentResult
import mx.finanzas.pareja.domain.paidInstant
import mx.finanzas.pareja.domain.CardDraftResult
import mx.finanzas.pareja.domain.CardEditDraft
import mx.finanzas.pareja.domain.CardEditResult
import mx.finanzas.pareja.domain.CardField

data class CardsUiState(
    val cards: List<Card> = emptyList(),
    /** Tarjetas archivadas: ocultas del resto de la app pero con sus gastos intactos. */
    val archived: List<Card> = emptyList(),
    /** Gasto del mes por id de tarjeta; solo para las que tienen presupuesto. */
    val spend: Map<String, MonthSpend> = emptyMap(),
    val loading: Boolean = true,
    val errors: Map<CardField, String> = emptyMap(),
    val message: String? = null,
    val added: Boolean = false,
    /** Errores del formulario de edición y señal de que se guardó (para cerrar el diálogo). */
    val editErrors: Map<CardField, String> = emptyMap(),
    val edited: Boolean = false,
    /** Cómo va el pago del último ciclo cerrado de cada tarjeta, y los pagos registrados en ese ciclo. */
    val statuses: Map<String, CardCycleStatus> = emptyMap(),
    val payments: Map<String, List<CardPayment>> = emptyMap(),
    val paymentError: String? = null,
    val paymentSaved: Boolean = false,
    /** Lo que la app calcula que se debe en total por tarjeta (para compararlo con el banco). */
    val appDebts: Map<String, Double> = emptyMap(),
    /** Última captura del saldo del banco por tarjeta. */
    val balanceChecks: Map<String, CardBalanceCheck> = emptyMap(),
    val balanceError: String? = null,
    val balanceSaved: Boolean = false,
)

private val DEFAULT_ZONE = TimeZone.of("America/Mexico_City")

class CardsViewModel(
    private val repo: FinanceRepository,
    private val userId: String,
    private val householdId: String,
    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    private val zone: TimeZone = DEFAULT_ZONE,
) : ViewModel() {
    private val _state = MutableStateFlow(CardsUiState())
    val state: StateFlow<CardsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val (archived, active) = repo.cards().partition { it.archived }
                val spend = active.filter { it.monthlyBudget != null }
                    .mapNotNull { card -> repo.cardMonthSpend(card.id, today)?.let { card.id to it } }
                    .toMap()
                val snapshots = repo.cardSnapshots(active.map { it.id }, today)
                val statuses = snapshots.mapNotNull { s -> s.status?.let { s.cardId to it } }.toMap()
                val appDebts = snapshots.associate { it.cardId to debtTotal(it.estimate, it.status) }
                val payments = statuses.filterValues { it.paidAmount > 0 }
                    .mapValues { (cardId, status) -> repo.cardPayments(cardId, status.cutoffDate) }
                // Si la tabla aún no existe en el servidor, la pantalla sigue funcionando sin la conciliación.
                val checks = runCatching { repo.balanceChecks() }.getOrDefault(emptyList())
                    .groupBy { it.cardId }.mapValues { (_, list) -> list.first() }
                _state.update {
                    it.copy(
                        cards = active, archived = archived, spend = spend, statuses = statuses,
                        payments = payments, appDebts = appDebts, balanceChecks = checks, loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = userMessage(e)) }
            }
        }
    }

    fun addCard(draft: CardDraft) {
        when (val result = draft.validate()) {
            is CardDraftResult.Invalid -> _state.update { it.copy(errors = result.errors, added = false) }
            is CardDraftResult.Valid -> viewModelScope.launch {
                val v = result.value
                try {
                    repo.addCard(
                        NewCard(
                            householdId = householdId, ownerId = userId, name = v.name, bank = v.bank,
                            last4 = v.last4, creditLimit = v.creditLimit, cutoffDay = v.cutoffDay, dueDay = v.dueDay,
                        ),
                    )
                    _state.update { it.copy(errors = emptyMap(), added = true) }
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(message = userMessage(e)) }
                }
            }
        }
    }

    fun archive(id: String) {
        viewModelScope.launch {
            try {
                repo.archiveCard(id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    /** Valida y guarda la edición completa de una tarjeta propia (datos, presupuesto y color). */
    fun saveEdit(id: String, draft: CardEditDraft) {
        when (val result = draft.validate()) {
            is CardEditResult.Invalid -> _state.update { it.copy(editErrors = result.errors, edited = false) }
            is CardEditResult.Valid -> viewModelScope.launch {
                val v = result.value
                try {
                    repo.editCard(
                        id,
                        CardEditPatch(
                            name = v.card.name, bank = v.card.bank, last4 = v.card.last4,
                            creditLimit = v.card.creditLimit, cutoffDay = v.card.cutoffDay, dueDay = v.card.dueDay,
                            monthlyBudget = v.monthlyBudget, color = v.color, hasCashback = v.hasCashback,
                        ),
                    )
                    _state.update { it.copy(editErrors = emptyMap(), edited = true) }
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(message = userMessage(e)) }
                }
            }
        }
    }

    fun consumeEdited() = _state.update { it.copy(edited = false, editErrors = emptyMap()) }

    /** Registra un pago a una tarjeta propia para el ciclo que indica [status]. */
    fun registerPayment(card: Card, status: CardCycleStatus, draft: CardPaymentDraft) {
        if (card.ownerId != userId) return
        when (val result = draft.validate(today)) {
            is CardPaymentResult.Invalid -> _state.update { it.copy(paymentError = result.message, paymentSaved = false) }
            is CardPaymentResult.Valid -> savePayment {
                repo.addCardPayment(
                    NewCardPayment(
                        householdId = householdId, ownerId = userId, cardId = card.id,
                        cutoffDate = status.cutoffDate, amount = result.amount,
                        paidAt = paidInstant(result.date, today, Clock.System.now(), zone),
                        method = result.method, notes = result.notes,
                    ),
                )
            }
        }
    }

    /** Edita un pago propio (monto, fecha, forma de pago y nota). Sin cambiar el día se conserva su hora. */
    fun updatePayment(payment: CardPayment, draft: CardPaymentDraft) {
        if (payment.ownerId != userId) return
        when (val result = draft.validate(today)) {
            is CardPaymentResult.Invalid -> _state.update { it.copy(paymentError = result.message, paymentSaved = false) }
            is CardPaymentResult.Valid -> savePayment {
                repo.updateCardPayment(
                    payment.id,
                    CardPaymentPatch(
                        amount = result.amount,
                        paidAt = paidInstant(result.date, today, Clock.System.now(), zone, original = payment.paidAt),
                        method = result.method, notes = result.notes,
                    ),
                )
            }
        }
    }

    private fun savePayment(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
                _state.update { it.copy(paymentError = null, paymentSaved = true) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    fun deletePayment(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteCardPayment(id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    fun consumePaymentSaved() = _state.update { it.copy(paymentSaved = false, paymentError = null) }

    /** Guarda el saldo utilizado que muestra el banco hoy para una tarjeta propia; sirve para conciliar. */
    fun saveBalanceCheck(card: Card, text: String) {
        if (card.ownerId != userId) return
        val balance = parseBankBalance(text)
        if (balance == null) {
            _state.update { it.copy(balanceError = "Escribe el saldo que muestra el banco (puede ser 0)", balanceSaved = false) }
            return
        }
        viewModelScope.launch {
            try {
                repo.addBalanceCheck(
                    NewCardBalanceCheck(
                        householdId = householdId, ownerId = userId, cardId = card.id,
                        bankBalance = balance, checkedOn = today,
                    ),
                )
                _state.update { it.copy(balanceError = null, balanceSaved = true) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // El error se muestra dentro del diálogo abierto: arriba de la pantalla quedaría detrás de él.
                _state.update { it.copy(balanceError = userMessage(e), balanceSaved = false) }
            }
        }
    }

    fun consumeBalanceSaved() = _state.update { it.copy(balanceSaved = false, balanceError = null) }

    /** `amount` null quita el presupuesto. Un monto cero o negativo se rechaza antes de llegar al servidor. */
    fun setBudget(id: String, amount: Double?) {
        if (amount != null && amount <= 0) {
            _state.update { it.copy(message = "El presupuesto debe ser mayor a cero") }
            return
        }
        viewModelScope.launch {
            try {
                repo.setCardBudget(id, amount)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    fun restore(id: String) {
        viewModelScope.launch {
            try {
                repo.restoreCard(id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }

    fun consumeAdded() = _state.update { it.copy(added = false, errors = emptyMap()) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
}
