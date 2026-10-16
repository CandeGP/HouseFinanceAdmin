package mx.finanzas.pareja.domain

import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxStatus

/** Cargo que se repite cada mes (suscripción, servicio, renta). [amount] es el monto típico. */
data class RecurringCharge(
    val merchant: String,
    val amount: Double,
    val lastDate: LocalDate,
    val nextExpected: LocalDate,
    val months: Int,
    val cardId: String?,
)

/** Cuánto historial se revisa y cuántos meses distintos hacen falta para llamarlo recurrente. */
const val RECURRING_LOOKBACK_MONTHS = 6
private const val MIN_MONTHS = 3

/** Un cargo cuyo último movimiento es más viejo que esto se da por cancelado. */
private const val ACTIVE_DAYS = 45

/** Los montos deben estar a ±20% de la mediana (al menos el 80% de las veces). */
private const val AMOUNT_TOLERANCE = 0.20
private const val MIN_SHARE_IN_RANGE = 0.8

/** Más de ~1 cargo al mes es otra cosa (café diario, viajes), no una suscripción. */
private const val MAX_CHARGES_PER_MONTH = 1.5

/** Se lista si su siguiente cargo cae entre hace 10 días (atraso) y dentro de 31 días. */
private const val GRACE_DAYS = 10
private const val HORIZON_DAYS = 31

private fun monthIndex(day: LocalDate) = day.year * 12 + day.monthNumber

private fun median(sorted: List<Double>): Double {
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/**
 * Detecta cargos mensuales en el historial: mismo comercio en 3 meses distintos o más, monto parecido y
 * a lo más ~1 cargo al mes, todavía activo (último cargo hace menos de 45 días). Solo cuenta compras de
 * contado confirmadas. Devuelve los que se esperan pronto, ordenados por su siguiente fecha.
 *
 * shortcut: solo cadencia mensual (no semanal ni anual), y el comercio se agrupa por nombre normalizado;
 * si hace falta más precisión, ligar el cargo a una tabla propia que la persona pueda confirmar.
 */
fun detectRecurring(transactions: List<Transaction>, today: LocalDate): List<RecurringCharge> {
    val since = today.minus(RECURRING_LOOKBACK_MONTHS, DateTimeUnit.MONTH)
    return transactions
        .filter {
            it.status == TxStatus.CONFIRMED && it.paymentMode == PaymentMode.CONTADO &&
                it.occurredAt > since && it.occurredAt <= today
        }
        .groupBy { merchantKey(it.merchant).replace(Regex("\\d+"), "") }
        .filterKeys { it.isNotEmpty() }
        .values
        .mapNotNull { group -> recurringOf(group, today) }
        .sortedBy { it.nextExpected }
}

private fun recurringOf(group: List<Transaction>, today: LocalDate): RecurringCharge? {
    val months = group.map { monthIndex(it.occurredAt) }.distinct().size
    if (months < MIN_MONTHS || group.size > months * MAX_CHARGES_PER_MONTH) return null

    val typical = median(group.map { it.amount }.sorted())
    val inRange = group.count { abs(it.amount - typical) <= typical * AMOUNT_TOLERANCE }
    if (inRange < group.size * MIN_SHARE_IN_RANGE) return null

    val last = group.maxBy { it.occurredAt }
    if (last.occurredAt.daysUntil(today) > ACTIVE_DAYS) return null

    val next = last.occurredAt.plus(1, DateTimeUnit.MONTH)
    if (next < today.minus(GRACE_DAYS, DateTimeUnit.DAY) || next > today.plus(HORIZON_DAYS, DateTimeUnit.DAY)) return null

    return RecurringCharge(
        merchant = last.merchant.trim(),
        amount = typical,
        lastDate = last.occurredAt,
        nextExpected = next,
        months = months,
        cardId = group.mapNotNull { it.cardId }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
    )
}
