package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import mx.finanzas.pareja.data.LoanDirection

/** Cada cuántos días de antigüedad se recuerda un préstamo abierto. */
const val LOAN_REMINDER_DAYS = 15

/**
 * Recordatorios de préstamos abiertos: a los 15 días y luego cada 15 ("Hermana te debe $300 · hace 15 días").
 * La llave lleva el tramo (15, 30, 45…), así cada recordatorio sale una sola vez. Liquidados y perdonados no avisan.
 * Recibe solo los préstamos de quien usa el teléfono.
 */
fun loanReminders(views: List<LoanView>, today: LocalDate, everyDays: Int = LOAN_REMINDER_DAYS): List<CycleAlert> =
    views
        .filter { it.state == LoanState.OPEN }
        .mapNotNull { view ->
            val days = view.loan.occurredOn.daysUntil(today)
            if (days < everyDays) return@mapNotNull null
            val lent = view.loan.direction == LoanDirection.LENT
            days to CycleAlert(
                key = "loan:${view.loan.id}:${days / everyDays * everyDays}",
                kind = AlertKind.LOAN,
                cardId = "",
                daysLeft = 0,
                title = if (lent) "${view.loan.person} te debe ${formatMxn(view.remaining)}"
                else "Le debes ${formatMxn(view.remaining)} a ${view.loan.person}",
                text = "Hace $days días que " + (if (lent) "se lo prestaste" else "te lo prestó") +
                    ". Registra el abono en Pagos › Préstamos cuando se salde.",
                amount = view.remaining,
            )
        }
        .sortedByDescending { it.first }
        .map { it.second }
