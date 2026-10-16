package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.CycleEstimate

enum class AlertKind { CUTOFF, DUE, BUDGET, BUDGET_NEAR }

/** Ciclo vigente y ciclo anterior de una tarjeta (el anterior puede tener un pago aún pendiente). */
data class CardCycles(val card: Card, val current: CycleEstimate?, val previous: CycleEstimate?)

data class CycleAlert(
    /** Estable por tarjeta, tipo y fecha: sirve para no avisar dos veces lo mismo. */
    val key: String,
    val kind: AlertKind,
    val cardId: String,
    val daysLeft: Int,
    val title: String,
    val text: String,
    val amount: Double?,
)

/** Días de anticipación con los que se avisa un corte o un pago. */
const val DEFAULT_ALERT_LEAD_DAYS = 3

/** Días que faltan en los que se muestra el aviso: 3 días antes, 1 día antes y el mismo día. */
val ALERT_REMINDER_DAYS: Set<Int> = setOf(3, 1, 0)

/** Identifica un recordatorio concreto (el mismo evento avisa en 3 momentos distintos). */
val CycleAlert.reminderKey: String get() = "$key@$daysLeft"

private fun whenText(daysLeft: Int) = when (daysLeft) {
    0 -> "hoy"
    1 -> "mañana"
    else -> "en $daysLeft días"
}

/**
 * Avisos de corte y de pago que caen en los próximos [leadDays] días (incluido hoy).
 * El pago que sigue a un corte reciente vive en el ciclo anterior, por eso se mira también ahí.
 * No sabe si ya pagaste: el aviso se deja de mostrar cuando pasa la fecha límite.
 */
fun upcomingAlerts(
    cards: List<CardCycles>,
    today: LocalDate,
    leadDays: Int = DEFAULT_ALERT_LEAD_DAYS,
): List<CycleAlert> {
    val alerts = mutableListOf<CycleAlert>()
    for ((card, current, previous) in cards) {
        val label = cardLabel(card)

        current?.let { estimate ->
            val daysLeft = today.daysUntil(estimate.cycleEnd)
            if (daysLeft in 0..leadDays) {
                alerts += CycleAlert(
                    key = "${card.id}:${AlertKind.CUTOFF}:${estimate.cycleEnd}",
                    kind = AlertKind.CUTOFF, cardId = card.id, daysLeft = daysLeft,
                    title = "Corte de $label ${whenText(daysLeft)}",
                    text = "Lo que gastes después del ${formatDayMonthEs(estimate.cycleEnd)} entra al siguiente ciclo.",
                    amount = null,
                )
            }
        }

        for (estimate in listOfNotNull(previous, current)) {
            val daysLeft = today.daysUntil(estimate.dueDate)
            val key = "${card.id}:${AlertKind.DUE}:${estimate.dueDate}"
            if (daysLeft in 0..leadDays && alerts.none { it.key == key }) {
                alerts += CycleAlert(
                    key = key, kind = AlertKind.DUE, cardId = card.id, daysLeft = daysLeft,
                    title = "Pago de $label vence ${whenText(daysLeft)}",
                    text = "Pago estimado ${formatMxn(estimate.cycleTotal)} · fecha límite ${formatDayMonthEs(estimate.dueDate)}.",
                    amount = estimate.cycleTotal,
                )
            }
        }
    }
    return alerts.sortedWith(compareBy({ it.daysLeft }, { it.title }))
}
