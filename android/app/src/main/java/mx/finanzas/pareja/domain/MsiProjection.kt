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

/** La siguiente mensualidad de cada compra a meses que sigue en pie, las más pesadas primero. */
fun activePurchases(months: List<MsiMonth>): List<MsiInstallment> =
    months.flatMap { it.items }
        .groupBy { it.transactionId }
        .map { (_, items) -> items.minBy { it.number } }
        .sortedByDescending { it.amount }

/** Monto corto para las barras: 3,444.81 → «3.4k», 980 → «980». */
fun compactMxn(amount: Double): String =
    if (amount >= 1000) "%.1fk".format(java.util.Locale.US, amount / 1000) else "%.0f".format(java.util.Locale.US, amount)

/** Cuántas mensualidades y cuánto dinero falta pagar en total de las compras a meses. */
data class MsiPending(val installments: Int, val amount: Double, val lastMonth: MsiMonth?)

fun pendingInstallments(months: List<MsiMonth>): MsiPending = MsiPending(
    installments = months.sumOf { it.items.size },
    amount = months.sumOf { it.total },
    lastMonth = months.lastOrNull(),
)
