package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CardPayStatus

/** Último día de la primera quincena: los pagos que vencen del 1 al 15 van en la 1ª, el resto en la 2ª. */
const val QUINCENA_SPLIT_DAY = 15

/**
 * Lo que toca pagar de una tarjeta y cuándo. Si el último corte cerrado sigue sin pagarse, es lo que falta de
 * ese corte ([fromClosedCycle]); si no, es la estimación del ciclo abierto.
 */
data class NextPayment(
    val amount: Double,
    val dueDate: LocalDate,
    val fromClosedCycle: Boolean,
    val overdue: Boolean,
    val paidClosedCycle: Boolean,
)

fun nextPayment(view: CardCycleView, status: CardCycleStatus?, today: LocalDate): NextPayment {
    val unpaid = status.unpaidAmount
    if (status != null && unpaid > 0) {
        return NextPayment(unpaid, status.dueDate, fromClosedCycle = true, overdue = status.dueDate < today, paidClosedCycle = false)
    }
    return NextPayment(
        amount = view.estimate.cycleTotal,
        dueDate = view.estimate.dueDate,
        fromClosedCycle = false,
        overdue = false,
        paidClosedCycle = status?.status == CardPayStatus.PAID && status.targetAmount > 0,
    )
}

/** Una sola línea con la fecha: sin repetir montos. */
fun NextPayment.dueLine(): String {
    val due = formatDayMonthEs(dueDate)
    return when {
        overdue -> "Vencido el $due"
        fromClosedCycle -> "Vence $due"
        paidClosedCycle -> "Pagado ✓ · siguiente pago vence $due"
        else -> "Siguiente pago vence $due"
    }
}

data class Quincena(val total: Double, val cards: Int)
data class QuincenaTotals(val first: Quincena, val second: Quincena)

val NO_QUINCENAS = QuincenaTotals(Quincena(0.0, 0), Quincena(0.0, 0))

/**
 * Suma lo que vence este mes (o ya venció y falta) separado por quincena de la fecha de pago. Los pagos de
 * meses siguientes no cuentan: una tarjeta ya pagada no suma hasta su próximo corte.
 */
fun quincenaTotals(payments: List<NextPayment>, today: LocalDate): QuincenaTotals {
    val monthEnd = monthBounds(today.year, today.monthNumber).second
    val (first, second) = payments
        .filter { it.amount > 0 && it.dueDate <= monthEnd }
        .partition { it.dueDate.dayOfMonth <= QUINCENA_SPLIT_DAY }
    fun sum(list: List<NextPayment>) = Quincena(list.sumOf { it.amount }, list.size)
    return QuincenaTotals(sum(first), sum(second))
}
