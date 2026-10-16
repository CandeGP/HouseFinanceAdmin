package mx.finanzas.pareja.domain

import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.data.PaymentMethod

/** Formulario de "Registrar pago" de una tarjeta: monto, forma de pago y nota. */
data class CardPaymentDraft(
    val amountText: String = "",
    val method: PaymentMethod = PaymentMethod.TRANSFER,
    val notes: String = "",
) {
    fun validate(): CardPaymentResult {
        val amount = parseAmount(amountText) ?: return CardPaymentResult.Invalid("Monto inválido")
        return CardPaymentResult.Valid(amount, method, notes.trim().ifEmpty { null })
    }
}

sealed interface CardPaymentResult {
    data class Valid(val amount: Double, val method: PaymentMethod, val notes: String?) : CardPaymentResult
    data class Invalid(val message: String) : CardPaymentResult
}

/**
 * Línea que resume cómo va el pago del último ciclo cerrado. Siempre nombra su corte: el ciclo en
 * curso (el de "Corte: … · Pago: …") es otro y se confundiría con este.
 */
fun CardCycleStatus.payLabel(): String {
    val cut = "Corte del ${formatDayMonthEs(cutoffDate)}"
    return when (status) {
        CardPayStatus.PAID -> if (targetAmount <= 0) "$cut: sin pago" else "$cut: pagado ✓"
        CardPayStatus.MINIMUM_ONLY ->
            "$cut: mínimo cubierto · faltan ${formatMxn(remaining)} para no generar intereses"
        CardPayStatus.PARTIAL -> "$cut: pago parcial · faltan ${formatMxn(remaining)}"
        CardPayStatus.PENDING ->
            "$cut: pago pendiente ${formatMxn(remaining)} · vence ${formatDayMonthEs(dueDate)}"
        CardPayStatus.OVERDUE ->
            "$cut: vencido · faltan ${formatMxn(remaining)} (venció el ${formatDayMonthEs(dueDate)})"
        CardPayStatus.UNTRACKED -> "$cut (${formatMxn(targetAmount)}): sin registro de pago"
    }
}

/** True si ya no hay nada que avisar de este pago. */
val CardCycleStatus.isSettled: Boolean get() = status == CardPayStatus.PAID

/** Llave del aviso de pago de este ciclo; igual a la que genera [upcomingAlerts]. */
fun CardCycleStatus.dueAlertKey(cardId: String): String = "$cardId:${AlertKind.DUE}:$dueDate"
