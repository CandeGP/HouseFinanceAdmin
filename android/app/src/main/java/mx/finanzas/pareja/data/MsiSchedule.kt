package mx.finanzas.pareja.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Una mensualidad futura de una compra a meses, tal como la proyecta la RPC `msi_schedule`:
 * [number] de [installments] se paga en la fecha límite [dueDate] del ciclo que cierra el [cycleEnd].
 */
@Serializable
data class MsiInstallment(
    @SerialName("card_id") val cardId: String,
    @SerialName("transaction_id") val transactionId: String,
    val merchant: String,
    @SerialName("cycle_end") val cycleEnd: LocalDate,
    @SerialName("due_date") val dueDate: LocalDate,
    @SerialName("installment_number") val number: Int,
    val installments: Int,
    val amount: Double,
) {
    /** Última mensualidad: con ella termina la compra. */
    val isLast: Boolean get() = number >= installments
}
