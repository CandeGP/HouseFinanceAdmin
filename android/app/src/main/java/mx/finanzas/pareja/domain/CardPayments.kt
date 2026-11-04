package mx.finanzas.pareja.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayStatus
import mx.finanzas.pareja.data.PaymentDestination
import mx.finanzas.pareja.data.PaymentMethod

/** Formulario de "Registrar pago" de una tarjeta: monto, forma de pago, nota y fecha (null = hoy). */
data class CardPaymentDraft(
    val amountText: String = "",
    val method: PaymentMethod = PaymentMethod.TRANSFER,
    val notes: String = "",
    val date: LocalDate? = null,
    /** A dónde va lo que sobre del corte cerrado (adelanto al siguiente corte o abono a capital). */
    val excessTo: PaymentDestination = PaymentDestination.NEXT_CYCLE,
) {
    fun validate(today: LocalDate): CardPaymentResult {
        val amount = parseAmount(amountText) ?: return CardPaymentResult.Invalid("Monto inválido")
        if (date != null && date > today) return CardPaymentResult.Invalid("La fecha no puede ser futura")
        return CardPaymentResult.Valid(amount, method, notes.trim().ifEmpty { null }, date ?: today)
    }
}

sealed interface CardPaymentResult {
    data class Valid(
        val amount: Double,
        val method: PaymentMethod,
        val notes: String?,
        val date: LocalDate,
    ) : CardPaymentResult

    data class Invalid(val message: String) : CardPaymentResult
}

/** Una parte de un pago: cuánto y a dónde va. */
data class PaymentPart(val amount: Double, val destination: PaymentDestination)

/**
 * Reparte un pago: primero cubre lo que falta del corte cerrado ([remaining]); lo que sobre va a [excessTo].
 * Con el corte ya pagado ([remaining] = 0) todo el monto es excedente.
 */
fun splitPayment(amount: Double, remaining: Double, excessTo: PaymentDestination): List<PaymentPart> {
    val toCycle = minOf(amount, remaining.coerceAtLeast(0.0))
    val excess = amount - toCycle
    return listOfNotNull(
        PaymentPart(toCycle, PaymentDestination.CYCLE).takeIf { toCycle > 0 },
        PaymentPart(excess, excessTo).takeIf { excess > EXCESS_EPSILON },
    )
}

/** Menos que esto es redondeo, no un excedente. */
private const val EXCESS_EPSILON = 0.005

private const val NOON_HOUR = 12

/**
 * Instante del pago. De hoy: la hora actual. De un día anterior: mediodía de ese día (solo importa el día).
 * Al editar, [original] se conserva si el día no cambió.
 */
fun paidInstant(date: LocalDate, today: LocalDate, now: Instant, zone: TimeZone, original: Instant? = null): Instant =
    when {
        original != null && original.toLocalDateTime(zone).date == date -> original
        date == today -> now
        else -> LocalDateTime(date, LocalTime(NOON_HOUR, 0)).toInstant(zone)
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

/** Lo que aún se debe del último corte cerrado; 0 si está pagado o si no se lleva registro de pagos. */
val CardCycleStatus?.unpaidAmount: Double
    get() = when (this?.status) {
        CardPayStatus.PENDING, CardPayStatus.OVERDUE, CardPayStatus.PARTIAL, CardPayStatus.MINIMUM_ONLY ->
            remaining.coerceAtLeast(0.0)
        else -> 0.0
    }
