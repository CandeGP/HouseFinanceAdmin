package mx.finanzas.pareja.domain

import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Una categoría (o tarjeta) con su gasto del periodo actual contra el anterior. */
data class TrendRow(val label: String, val current: Double, val previous: Double) {
    val delta: Double get() = current - previous

    /** Cambio en %; null si antes no había gasto (no se puede comparar). */
    val deltaPct: Double? get() = if (previous > 0) delta / previous * 100 else null
}

/** Junta dos cortes de gasto por nombre; ordena por lo más grande del periodo actual, luego del anterior. */
fun trendRows(current: List<SpendingSlice>, previous: List<SpendingSlice>): List<TrendRow> {
    val now = current.associate { it.label to it.total }
    val before = previous.associate { it.label to it.total }
    return (now.keys + before.keys)
        .map { TrendRow(it, now[it] ?: 0.0, before[it] ?: 0.0) }
        .sortedWith(compareByDescending<TrendRow> { it.current }.thenByDescending { it.previous }.thenBy { it.label })
}

/** Lo que va del mes y el mismo tramo del mes anterior (día 1 hasta el mismo día; si no existe, el último). */
fun monthToDateRanges(today: LocalDate): Pair<ClosedRange<LocalDate>, ClosedRange<LocalDate>> {
    val firstThis = LocalDate(today.year, today.month, 1)
    val firstPrev = firstThis.minus(1, DateTimeUnit.MONTH)
    val prevLast = monthBounds(firstPrev.year, firstPrev.monthNumber).second
    val prevEnd = minOf(LocalDate(firstPrev.year, firstPrev.month, today.dayOfMonth.coerceAtMost(prevLast.dayOfMonth)), prevLast)
    return (firstThis..today) to (firstPrev..prevEnd)
}

/** "+12%", "-8%" o "nuevo" (sin gasto antes); null si no cambió. */
fun TrendRow.changeLabel(): String? {
    val pct = deltaPct
    return when {
        abs(delta) < 0.005 -> null
        pct == null -> "nuevo"
        else -> "%+d%%".format(Math.round(pct))
    }
}
