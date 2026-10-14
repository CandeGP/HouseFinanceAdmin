package mx.finanzas.pareja.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Cabecera del estado de cuenta tal como la espera la RPC `import_statement`. */
@Serializable
data class StatementImportSummary(
    val bank: String?,
    @SerialName("card_last4") val cardLast4: String?,
    @SerialName("period_start") val periodStart: LocalDate?,
    @SerialName("period_end") val periodEnd: LocalDate?,
    @SerialName("cutoff_date") val cutoffDate: LocalDate,
    @SerialName("due_date") val dueDate: LocalDate?,
    @SerialName("credit_limit") val creditLimit: Double?,
    @SerialName("pay_no_interest") val payNoInterest: Double?,
    @SerialName("minimum_payment") val minimumPayment: Double?,
    @SerialName("file_path") val filePath: String?,
)

/** Cargo del estado que falta en la app (monto positivo). */
@Serializable
data class ImportMovement(
    val date: LocalDate,
    val description: String,
    val amount: Double,
)

/** Compra a meses del estado; `current` es el número de pago de ESTE corte. */
@Serializable
data class ImportInstallment(
    @SerialName("purchase_date") val purchaseDate: LocalDate,
    val description: String,
    @SerialName("original_amount") val originalAmount: Double,
    val current: Int,
    val total: Int,
)

@Serializable
data class ImportResult(
    val movements: Int,
    val installments: Int,
)
