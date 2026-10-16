package mx.finanzas.pareja.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Edición completa de una tarjeta (los campos se serializan siempre). */
@Serializable
data class CardPatch(
    val name: String,
    val bank: String,
    val last4: String,
    @SerialName("credit_limit") val creditLimit: Double,
    @SerialName("cutoff_day") val cutoffDay: Int,
    @SerialName("due_day") val dueDay: Int,
)

/**
 * Edición completa de una tarjeta propia (todos los campos se serializan, incluidos los null, así
 * quitar el presupuesto o el color los limpia en el servidor).
 */
@Serializable
data class CardEditPatch(
    val name: String,
    val bank: String,
    val last4: String,
    @SerialName("credit_limit") val creditLimit: Double,
    @SerialName("cutoff_day") val cutoffDay: Int,
    @SerialName("due_day") val dueDay: Int,
    @SerialName("monthly_budget") val monthlyBudget: Double?,
    val color: String?,
)

/**
 * Único punto de acceso a datos de la app. La implementación real habla con Supabase;
 * las pruebas usan un doble en memoria.
 */
interface FinanceRepository {
    // Sesión
    suspend fun currentUserId(): String?
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun signOut()

    // Hogar
    suspend fun members(): List<Member>
    suspend fun createHousehold(name: String, displayName: String): String
    suspend fun createInvite(householdId: String): String
    suspend fun acceptInvite(code: String, displayName: String): String

    // Catálogos
    suspend fun categories(): List<Category>

    /** Crea una categoría del hogar; los miembros pueden agregar las suyas. */
    suspend fun addCategory(householdId: String, name: String)
    suspend fun cards(): List<Card>
    suspend fun addCard(card: NewCard)
    suspend fun updateCard(id: String, patch: CardPatch)

    /** Guarda la edición completa de una tarjeta propia (datos, presupuesto y color). */
    suspend fun editCard(id: String, patch: CardEditPatch)
    suspend fun archiveCard(id: String)

    /** Deshace [archiveCard]: la tarjeta vuelve a mostrarse con todos sus gastos. */
    suspend fun restoreCard(id: String)
    suspend fun cycleEstimate(cardId: String, ref: LocalDate): CycleEstimate?

    /** Gasto del mes calendario de [ref] frente al presupuesto de la tarjeta. */
    suspend fun cardMonthSpend(cardId: String, ref: LocalDate): MonthSpend?

    /** Fija el presupuesto mensual de una tarjeta propia; null lo quita. */
    suspend fun setCardBudget(id: String, amount: Double?)

    /** Estado de pago del último ciclo cerrado de la tarjeta; null si la tarjeta no existe. */
    suspend fun cardCycleStatus(cardId: String, ref: LocalDate): CardCycleStatus?

    /** Pagos registrados a la tarjeta para el ciclo que cierra en [cutoff], el más reciente primero. */
    suspend fun cardPayments(cardId: String, cutoff: LocalDate): List<CardPayment>
    suspend fun addCardPayment(payment: NewCardPayment)
    suspend fun deleteCardPayment(id: String)

    // Gastos
    suspend fun transactions(from: LocalDate, to: LocalDate): List<Transaction>
    suspend fun transaction(id: String): Transaction?
    suspend fun addTransaction(tx: NewTransaction)
    suspend fun updateTransaction(id: String, patch: TransactionPatch)
    suspend fun deleteTransaction(id: String)

    // Captura automática y bandeja de revisión
    suspend fun ingestCapture(
        householdId: String,
        merchant: String,
        amount: Double,
        capturedAt: Instant,
        cardLast4: String?,
        source: TxSource,
        raw: String?,
    ): IngestResult

    suspend fun pendingTransactions(): List<Transaction>
    suspend fun confirmTransaction(id: String, cardId: String, categoryId: String?)

    // Estados de cuenta
    suspend fun importStatement(
        cardId: String,
        summary: StatementImportSummary,
        movements: List<ImportMovement>,
        installments: List<ImportInstallment>,
    ): ImportResult

    // Pagos en efectivo / transferencia
    suspend fun manualPayments(from: Instant, toExclusive: Instant): List<ManualPayment>
    suspend fun manualPayment(id: String): ManualPayment?

    /** "Pagado a" de los pagos ya registrados (más recientes primero, con repetidos), para sugerirlos. */
    suspend fun payees(): List<String>
    suspend fun addManualPayment(payment: NewManualPayment)
    suspend fun updateManualPayment(id: String, patch: ManualPaymentPatch)
    suspend fun deleteManualPayment(id: String)

    // Préstamos entre personas (aparte de los gastos)
    suspend fun loans(): List<Loan>
    suspend fun loanRepayments(): List<LoanRepayment>
    suspend fun addLoan(loan: NewLoan)
    suspend fun deleteLoan(id: String)

    /** Perdonar saca el préstamo de lo que se debe; reabrirlo lo devuelve. */
    suspend fun setLoanForgiven(id: String, forgiven: Boolean)
    suspend fun addLoanRepayment(repayment: NewLoanRepayment)
    suspend fun deleteLoanRepayment(id: String)

    // Evidencias (bucket privado `evidence`)
    suspend fun uploadEvidence(path: String, bytes: ByteArray, mimeType: String)
    suspend fun deleteEvidence(path: String)

    /** URL firmada y temporal para ver una evidencia. */
    suspend fun evidenceUrl(path: String): String
}
