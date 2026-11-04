package mx.finanzas.pareja.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

/**
 * Aplica los abonos a capital al calendario: cada abono baja, en la misma proporción, las mensualidades de su
 * tarjeta que vencen después del día en que se hizo (se descuenta de todos los meses que faltan). Una
 * mensualidad que llega a cero desaparece. Los abonos se aplican en el orden en que se hicieron.
 *
 * shortcut: el calendario solo trae lo que falta desde hoy, así que un abono viejo se reparte sobre menos meses
 * de los que tenía al hacerse; si hace falta más precisión, guardar el calendario original de cada compra.
 */
fun List<MsiInstallment>.withCapitalCredits(credits: List<CardPayment>): List<MsiInstallment> {
    val capital = credits.filter { it.destination == PaymentDestination.CAPITAL }.sortedBy { it.paidAt }
    if (capital.isEmpty()) return this
    var current = this
    for (credit in capital) {
        val paidOn = credit.paidAt.toLocalDateTime(TimeZone.UTC).date
        fun affected(item: MsiInstallment) = item.cardId == credit.cardId && item.dueDate > paidOn
        val remaining = current.filter(::affected).sumOf { it.amount }
        if (remaining <= 0.0) continue
        val factor = ((remaining - credit.amount) / remaining).coerceAtLeast(0.0)
        current = current.map { if (affected(it)) it.copy(amount = Math.round(it.amount * factor * 100) / 100.0) else it }
    }
    return current.filter { it.amount > 0.005 }
}
