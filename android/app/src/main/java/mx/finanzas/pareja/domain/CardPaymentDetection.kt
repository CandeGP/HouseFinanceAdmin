package mx.finanzas.pareja.domain

import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import mx.finanzas.pareja.data.CardPayment

// "Pago por SPEI", "BMOVIL.PAGO TDC", "¡Gracias por tu pago!", "Pago por transferencia SPEI"…
private val PAYMENT_HINT = Regex("""pago|spei|transferencia|dep[oó]sito""", RegexOption.IGNORE_CASE)

// Abonos que no son un pago hecho a la tarjeta: devoluciones, bonificaciones y "Abono de <compra a meses>".
private val NOT_A_PAYMENT = Regex("""devoluci|reembolso|bonificaci|cancelaci|ajuste|abono de""", RegexOption.IGNORE_CASE)

private fun cents(value: Double): Long = Math.round(abs(value) * 100)

/** True si este abono del estado de cuenta es un pago que hiciste a la tarjeta. */
fun StatementMovement.isCardPayment(): Boolean =
    amount < 0 && PAYMENT_HINT.containsMatchIn(description) && !NOT_A_PAYMENT.containsMatchIn(description)

/**
 * Corte del ciclo que pagan los abonos de este estado: el anterior al del estado.
 * (Lo que pagas durante el periodo del estado N liquida el saldo del estado N-1.)
 */
fun previousCutoff(summary: StatementSummary): LocalDate =
    summary.periodStart?.minus(1, DateTimeUnit.DAY) ?: summary.cutoffDate.minus(1, DateTimeUnit.MONTH)

/** Pago detectado en un estado de cuenta; [alreadyRegistered] si ya lo capturaste a mano con el mismo monto. */
data class DetectedPayment(val movement: StatementMovement, val alreadyRegistered: Boolean) {
    val amount: Double get() = abs(movement.amount)
}

/**
 * Pagos del estado que se pueden registrar. Cada pago ya capturado se empareja con a lo más un abono
 * del mismo monto, para no duplicarlo.
 */
fun detectCardPayments(movements: List<StatementMovement>, existing: List<CardPayment>): List<DetectedPayment> {
    val unused = existing.map { cents(it.amount) }.toMutableList()
    return movements.filter { it.isCardPayment() }.sortedBy { it.operationDate }.map { movement ->
        DetectedPayment(movement, alreadyRegistered = unused.remove(cents(movement.amount)))
    }
}
