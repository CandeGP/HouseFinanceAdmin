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
    suspend fun cards(): List<Card>
    suspend fun addCard(card: NewCard)
    suspend fun updateCard(id: String, patch: CardPatch)
    suspend fun archiveCard(id: String)

    /** Deshace [archiveCard]: la tarjeta vuelve a mostrarse con todos sus gastos. */
    suspend fun restoreCard(id: String)
    suspend fun cycleEstimate(cardId: String, ref: LocalDate): CycleEstimate?

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
    suspend fun addManualPayment(payment: NewManualPayment)
    suspend fun updateManualPayment(id: String, patch: ManualPaymentPatch)
    suspend fun deleteManualPayment(id: String)

    // Evidencias (bucket privado `evidence`)
    suspend fun uploadEvidence(path: String, bytes: ByteArray, mimeType: String)
    suspend fun deleteEvidence(path: String)

    /** URL firmada y temporal para ver una evidencia. */
    suspend fun evidenceUrl(path: String): String
}
