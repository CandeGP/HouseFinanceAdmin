package mx.finanzas.pareja.domain

import kotlin.math.roundToInt
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.MonthSpend

/** Tarjeta con su gasto del mes calendario (null si no se pudo consultar). */
data class CardMonthSpend(val card: Card, val spend: MonthSpend?)

/**
 * Avisos de presupuesto: una tarjeta cuyo gasto del mes **supera** su presupuesto mensual.
 * Gastar exactamente el presupuesto no cuenta. La clave incluye el mes, así que avisa una vez por
 * tarjeta y mes; al cambiar de mes el contador empieza de nuevo.
 */
fun budgetAlerts(items: List<CardMonthSpend>): List<CycleAlert> =
    items.mapNotNull { (card, spend) ->
        val budget = spend?.budget ?: return@mapNotNull null
        if (spend.spent <= budget) return@mapNotNull null

        val over = spend.spent - budget
        val percent = (spend.spent / budget * 100).roundToInt()
        val month = "${spend.monthStart.year}-${spend.monthStart.monthNumber.toString().padStart(2, '0')}"
        over to CycleAlert(
            key = "${card.id}:${AlertKind.BUDGET}:$month",
            kind = AlertKind.BUDGET,
            cardId = card.id,
            daysLeft = 0,
            title = "Superaste el presupuesto de ${cardLabel(card)}",
            text = "Llevas ${formatMxn(spend.spent)} de ${formatMxn(budget)} este mes ($percent%), " +
                "${formatMxn(over)} de más.",
            amount = spend.spent,
        )
    }.sortedByDescending { it.first }.map { it.second }
