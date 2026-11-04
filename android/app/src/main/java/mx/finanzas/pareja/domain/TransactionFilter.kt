package mx.finanzas.pareja.domain

import kotlinx.datetime.LocalDate
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxStatus

/** Cómo se ordenan los gastos de Gastos: por fecha (lo normal), o por monto. */
enum class TxSort(val label: String) {
    DATE("Fecha"),
    HIGHEST("Mayor gasto"),
    LOWEST("Menor gasto"),
}

/** Cuánto suman los gastos confirmados de cada día (el mismo criterio que el total del mes). */
fun dayTotals(items: List<Transaction>): Map<LocalDate, Double> =
    items.filter { it.status == TxStatus.CONFIRMED }
        .groupBy { it.occurredAt }
        .mapValues { (_, day) -> day.sumOf { it.amount } }

/** Gastos de una sola tarjeta (null = todas), ordenados; a igual monto o fecha gana el más reciente. */
fun filterAndSort(items: List<Transaction>, cardId: String?, sort: TxSort): List<Transaction> {
    val byCard = if (cardId == null) items else items.filter { it.cardId == cardId }
    val newestFirst = byCard.sortedByDescending { it.occurredAt }
    return when (sort) {
        TxSort.DATE -> newestFirst
        TxSort.HIGHEST -> newestFirst.sortedByDescending { it.amount }
        TxSort.LOWEST -> newestFirst.sortedBy { it.amount }
    }
}
