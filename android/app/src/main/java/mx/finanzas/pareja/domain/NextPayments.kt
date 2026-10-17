package mx.finanzas.pareja.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
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

/** Todo lo que se debe pagar de una tarjeta: el corte cerrado sin pagar (si lo hay) y el ciclo abierto. */
fun paymentObligations(view: CardCycleView, status: CardCycleStatus?, today: LocalDate): List<NextPayment> {
    val unpaid = status.unpaidAmount
    val closed = if (status != null && unpaid > 0) {
        listOf(NextPayment(unpaid, status.dueDate, fromClosedCycle = true, overdue = status.dueDate < today, paidClosedCycle = false))
    } else emptyList()
    val open = NextPayment(
        amount = view.estimate.cycleTotal,
        dueDate = view.estimate.dueDate,
        fromClosedCycle = false,
        overdue = false,
        paidClosedCycle = status?.status == CardPayStatus.PAID && status.targetAmount > 0,
    )
    return closed + open
}

/** Lo que toca pagar primero: el corte cerrado sin pagar y, si no hay, el ciclo abierto. */
fun nextPayment(view: CardCycleView, status: CardCycleStatus?, today: LocalDate): NextPayment =
    paymentObligations(view, status, today).first()

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

/** [overdue] es la parte de [total] que ya venció y sigue sin pagarse. */
data class Quincena(val total: Double, val cards: Int, val overdue: Double = 0.0)

/** Si [nextFirstEnd] no es null, el primer bloque es la 1ª quincena del mes que viene (termina ese día). */
data class QuincenaTotals(val first: Quincena, val second: Quincena, val nextFirstEnd: LocalDate? = null)

val NO_QUINCENAS = QuincenaTotals(Quincena(0.0, 0), Quincena(0.0, 0))

private fun quincenaOf(list: List<NextPayment>) =
    Quincena(list.sumOf { it.amount }, list.size, list.filter { it.overdue }.sumOf { it.amount })

/**
 * Suma lo que vence por quincena. Del 1 al 15 del mes: la 1ª y la 2ª de este mes. Pasado el 15, la 1ª ya
 * no se puede pagar a tiempo, así que el primer bloque pasa a ser la 1ª quincena del mes que viene y el
 * segundo junta todo lo que falta de este mes (incluido lo vencido). Los pagos de meses posteriores no cuentan.
 */
fun quincenaTotals(obligations: List<NextPayment>, today: LocalDate): QuincenaTotals {
    val monthEnd = monthBounds(today.year, today.monthNumber).second
    val owed = obligations.filter { it.amount > 0 }
    if (today.dayOfMonth <= QUINCENA_SPLIT_DAY) {
        val (first, second) = owed.filter { it.dueDate <= monthEnd }
            .partition { it.dueDate.dayOfMonth <= QUINCENA_SPLIT_DAY }
        return QuincenaTotals(quincenaOf(first), quincenaOf(second))
    }
    val nextStart = monthEnd.plus(1, DateTimeUnit.DAY)
    val nextFirstEnd = LocalDate(nextStart.year, nextStart.month, QUINCENA_SPLIT_DAY)
    return QuincenaTotals(
        first = quincenaOf(owed.filter { it.dueDate in nextStart..nextFirstEnd }),
        second = quincenaOf(owed.filter { it.dueDate <= monthEnd }),
        nextFirstEnd = nextFirstEnd,
    )
}
