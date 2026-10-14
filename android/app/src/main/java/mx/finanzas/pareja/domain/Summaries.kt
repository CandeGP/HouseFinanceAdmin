package mx.finanzas.pareja.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import mx.finanzas.pareja.data.Card
import mx.finanzas.pareja.data.Category
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxStatus

const val NO_CATEGORY_LABEL = "Sin categoría"
const val NO_CARD_LABEL = "Sin tarjeta"

data class SpendingSlice(val label: String, val total: Double, val pct: Double)

/**
 * Gasto por categoría. Cuenta el monto total de cada compra en su fecha (lo que se gastó),
 * no las mensualidades; los gastos pendientes de revisión no suman.
 */
fun spendingByCategory(transactions: List<Transaction>, categories: List<Category>): List<SpendingSlice> {
    val names = categories.associate { it.id to it.name }
    return slices(transactions) { names[it.categoryId] ?: NO_CATEGORY_LABEL }
}

fun spendingByCard(transactions: List<Transaction>, cards: List<Card>): List<SpendingSlice> {
    val names = cards.associate { it.id to it.name }
    return slices(transactions) { names[it.cardId] ?: NO_CARD_LABEL }
}

private fun slices(transactions: List<Transaction>, label: (Transaction) -> String): List<SpendingSlice> {
    val confirmed = transactions.filter { it.status == TxStatus.CONFIRMED }
    val grand = confirmed.sumOf { it.amount }
    return confirmed
        .groupBy(label)
        .map { (name, items) ->
            val total = items.sumOf { it.amount }
            SpendingSlice(name, total, if (grand > 0) total / grand * 100 else 0.0)
        }
        .sortedWith(compareByDescending<SpendingSlice> { it.total }.thenBy { it.label })
}

/** Primer y último día del mes. */
fun monthBounds(year: Int, month: Int): Pair<LocalDate, LocalDate> {
    val first = LocalDate(year, month, 1)
    return first to first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
}
