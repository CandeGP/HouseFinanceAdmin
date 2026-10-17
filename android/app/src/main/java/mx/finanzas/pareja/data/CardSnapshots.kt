package mx.finanzas.pareja.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Estimación del ciclo y estado de pago de una tarjeta, juntos. Cada parte es null si la tarjeta no tiene datos. */
data class CardSnapshot(
    val cardId: String,
    val estimate: CycleEstimate?,
    val status: CardCycleStatus?,
)

/** Una consulta por tarjeta, en paralelo. Respaldo de [FinanceRepository.cardSnapshots] cuando no hay RPC única. */
internal suspend fun FinanceRepository.snapshotsOneByOne(cardIds: List<String>, ref: LocalDate): List<CardSnapshot> =
    coroutineScope {
        cardIds.map { id ->
            async { CardSnapshot(id, cycleEstimate(id, ref), cardCycleStatus(id, ref)) }
        }.awaitAll()
    }

/** Fila de la RPC `card_snapshots`: columnas de `card_cycle_estimate` y de `card_cycle_status` (prefijo `st_`). */
@Serializable
internal data class CardSnapshotRow(
    @SerialName("card_id") val cardId: String,
    @SerialName("cycle_start") val cycleStart: LocalDate? = null,
    @SerialName("cycle_end") val cycleEnd: LocalDate? = null,
    @SerialName("due_date") val dueDate: LocalDate? = null,
    @SerialName("cycle_contado_total") val cycleContadoTotal: Double? = null,
    @SerialName("cycle_msi_total") val cycleMsiTotal: Double? = null,
    @SerialName("cycle_total") val cycleTotal: Double? = null,
    @SerialName("msi_remaining") val msiRemaining: Double? = null,
    @SerialName("credit_limit") val creditLimit: Double? = null,
    @SerialName("usage_pct") val usagePct: Double? = null,
    @SerialName("st_cutoff_date") val stCutoffDate: LocalDate? = null,
    @SerialName("st_due_date") val stDueDate: LocalDate? = null,
    @SerialName("st_target_amount") val stTargetAmount: Double? = null,
    @SerialName("st_target_source") val stTargetSource: String? = null,
    @SerialName("st_minimum_payment") val stMinimumPayment: Double? = null,
    @SerialName("st_paid_amount") val stPaidAmount: Double? = null,
    @SerialName("st_remaining") val stRemaining: Double? = null,
    @SerialName("st_status") val stStatus: CardPayStatus? = null,
) {
    fun toSnapshot(): CardSnapshot = CardSnapshot(
        cardId = cardId,
        estimate = if (cycleStart != null && cycleEnd != null && dueDate != null) {
            CycleEstimate(
                cycleStart = cycleStart, cycleEnd = cycleEnd, dueDate = dueDate,
                cycleContadoTotal = cycleContadoTotal ?: 0.0, cycleMsiTotal = cycleMsiTotal ?: 0.0,
                cycleTotal = cycleTotal ?: 0.0, msiRemaining = msiRemaining ?: 0.0,
                creditLimit = creditLimit ?: 0.0, usagePct = usagePct ?: 0.0,
            )
        } else null,
        status = if (stCutoffDate != null && stDueDate != null && stStatus != null) {
            CardCycleStatus(
                cutoffDate = stCutoffDate, dueDate = stDueDate, targetAmount = stTargetAmount ?: 0.0,
                targetSource = stTargetSource ?: "estimate", minimumPayment = stMinimumPayment,
                paidAmount = stPaidAmount ?: 0.0, remaining = stRemaining ?: 0.0, status = stStatus,
            )
        } else null,
    )
}
