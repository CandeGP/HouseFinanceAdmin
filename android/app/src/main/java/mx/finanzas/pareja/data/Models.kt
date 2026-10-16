package mx.finanzas.pareja.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PaymentMode {
    @SerialName("contado") CONTADO,
    @SerialName("msi") MSI,
}

@Serializable
enum class TxSource(val wireName: String) {
    @SerialName("manual") MANUAL("manual"),
    @SerialName("notification") NOTIFICATION("notification"),
    @SerialName("email") EMAIL("email"),
    @SerialName("statement") STATEMENT("statement"),
}

@Serializable
enum class TxStatus {
    @SerialName("pending_review") PENDING_REVIEW,
    @SerialName("confirmed") CONFIRMED,
}

@Serializable
enum class MemberRole {
    @SerialName("admin") ADMIN,
    @SerialName("member") MEMBER,
}

@Serializable
data class Member(
    @SerialName("household_id") val householdId: String,
    @SerialName("user_id") val userId: String,
    val role: MemberRole,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String? = null,
)

@Serializable
data class Card(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val bank: String,
    val last4: String,
    @SerialName("credit_limit") val creditLimit: Double,
    @SerialName("cutoff_day") val cutoffDay: Int,
    @SerialName("due_day") val dueDay: Int,
    val color: String? = null,
    val archived: Boolean = false,
    /** Otros últimos 4 con los que aparece la tarjeta (digital, virtual o Google Wallet). */
    @SerialName("alt_last4") val altLast4: List<String> = emptyList(),
    /** Tope de gasto por mes calendario; null si no tiene presupuesto. */
    @SerialName("monthly_budget") val monthlyBudget: Double? = null,
)

/** Resultado de la RPC `card_month_spend`: gasto del mes de una tarjeta frente a su presupuesto. */
@Serializable
data class MonthSpend(
    @SerialName("month_start") val monthStart: LocalDate,
    @SerialName("month_end") val monthEnd: LocalDate,
    val spent: Double,
    val budget: Double? = null,
    val pct: Double? = null,
)

@Serializable
data class NewCard(
    @SerialName("household_id") val householdId: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val bank: String,
    val last4: String,
    @SerialName("credit_limit") val creditLimit: Double,
    @SerialName("cutoff_day") val cutoffDay: Int,
    @SerialName("due_day") val dueDay: Int,
    val color: String? = null,
)

/** Resultado de la RPC `card_cycle_estimate`. */
@Serializable
data class CycleEstimate(
    @SerialName("cycle_start") val cycleStart: LocalDate,
    @SerialName("cycle_end") val cycleEnd: LocalDate,
    @SerialName("due_date") val dueDate: LocalDate,
    @SerialName("cycle_contado_total") val cycleContadoTotal: Double,
    @SerialName("cycle_msi_total") val cycleMsiTotal: Double,
    @SerialName("cycle_total") val cycleTotal: Double,
    @SerialName("msi_remaining") val msiRemaining: Double,
    @SerialName("credit_limit") val creditLimit: Double,
    @SerialName("usage_pct") val usagePct: Double,
)

@Serializable
data class Transaction(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("card_id") val cardId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val merchant: String,
    val amount: Double,
    @SerialName("occurred_at") val occurredAt: LocalDate,
    @SerialName("payment_mode") val paymentMode: PaymentMode = PaymentMode.CONTADO,
    val installments: Int? = null,
    @SerialName("installment_number") val installmentNumber: Int? = null,
    val notes: String? = null,
    val source: TxSource = TxSource.MANUAL,
    val status: TxStatus = TxStatus.CONFIRMED,
)

@Serializable
data class NewTransaction(
    @SerialName("household_id") val householdId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("card_id") val cardId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val merchant: String,
    val amount: Double,
    @SerialName("occurred_at") val occurredAt: LocalDate,
    @SerialName("payment_mode") val paymentMode: PaymentMode = PaymentMode.CONTADO,
    val installments: Int? = null,
    @SerialName("installment_number") val installmentNumber: Int? = null,
    val notes: String? = null,
)

/**
 * Parche completo de edición. Sin valores por defecto a propósito: todos los campos se
 * serializan siempre (incluidos los null), así pasar de MSI a contado limpia las mensualidades.
 */
@Serializable
data class TransactionPatch(
    @SerialName("card_id") val cardId: String?,
    @SerialName("category_id") val categoryId: String?,
    val merchant: String,
    val amount: Double,
    @SerialName("occurred_at") val occurredAt: LocalDate,
    @SerialName("payment_mode") val paymentMode: PaymentMode,
    val installments: Int?,
    @SerialName("installment_number") val installmentNumber: Int?,
    val notes: String?,
)

/** Resultado de la RPC `ingest_capture`. */
@Serializable
data class IngestResult(
    @SerialName("tx_id") val txId: String,
    val duplicate: Boolean,
    @SerialName("resolved_card_id") val resolvedCardId: String? = null,
)

@Serializable
enum class PaymentMethod {
    @SerialName("cash") CASH,
    @SerialName("transfer") TRANSFER,
}

/** Pago en efectivo o transferencia, con evidencia opcional (ruta en el bucket `evidence`). */
@Serializable
data class ManualPayment(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("owner_id") val ownerId: String,
    val method: PaymentMethod,
    val amount: Double,
    @SerialName("paid_at") val paidAt: Instant,
    val concept: String,
    val counterparty: String? = null,
    val reference: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val notes: String? = null,
    @SerialName("evidence_path") val evidencePath: String? = null,
)

@Serializable
data class NewManualPayment(
    @SerialName("household_id") val householdId: String,
    @SerialName("owner_id") val ownerId: String,
    val method: PaymentMethod,
    val amount: Double,
    @SerialName("paid_at") val paidAt: Instant,
    val concept: String,
    val counterparty: String? = null,
    val reference: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val notes: String? = null,
    @SerialName("evidence_path") val evidencePath: String? = null,
)

/** Edición completa (sin valores por defecto: los null se envían para poder limpiar campos). */
@Serializable
data class ManualPaymentPatch(
    val method: PaymentMethod,
    val amount: Double,
    @SerialName("paid_at") val paidAt: Instant,
    val concept: String,
    val counterparty: String?,
    val reference: String?,
    @SerialName("category_id") val categoryId: String?,
    val notes: String?,
    @SerialName("evidence_path") val evidencePath: String?,
)
