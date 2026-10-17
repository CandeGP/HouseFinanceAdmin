package mx.finanzas.pareja.domain

import mx.finanzas.pareja.data.MsiInstallment

/** Lo que se paga de compras a meses en un mes (el de la fecha límite de pago). */
data class MsiMonth(val year: Int, val month: Int, val items: List<MsiInstallment>) {
    val total: Double get() = items.sumOf { it.amount }
    val label: String get() = formatMonthEs(year, month)

    /** Cuántas compras terminan de pagarse con las mensualidades de este mes. */
    val finishing: Int get() = items.count { it.isLast }
}

/**
 * Agrupa las mensualidades por mes de pago, en orden cronológico. Dentro de cada mes van primero las más
 * grandes, para ver de un vistazo qué pesa más.
 */
fun projectInstallments(rows: List<MsiInstallment>): List<MsiMonth> =
    rows.groupBy { it.dueDate.year to it.dueDate.monthNumber }
        .toSortedMap(compareBy({ it.first }, { it.second }))
        .map { (ym, items) -> MsiMonth(ym.first, ym.second, items.sortedByDescending { it.amount }) }

/** Cuántas mensualidades y cuánto dinero falta pagar en total de las compras a meses. */
data class MsiPending(val installments: Int, val amount: Double, val lastMonth: MsiMonth?)

fun pendingInstallments(months: List<MsiMonth>): MsiPending = MsiPending(
    installments = months.sumOf { it.items.size },
    amount = months.sumOf { it.total },
    lastMonth = months.lastOrNull(),
)
