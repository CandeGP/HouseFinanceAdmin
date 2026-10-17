package mx.finanzas.pareja.domain

import kotlin.math.abs
import kotlinx.datetime.daysUntil
import mx.finanzas.pareja.data.Transaction
import mx.finanzas.pareja.data.TxStatus

private const val MAX_DAYS_APART = 3
private const val MIN_MERCHANT_SIMILARITY = 0.5
private const val AMOUNT_TOLERANCE = 0.005

/** Dos cargos que parecen ser el mismo registrado dos veces. */
data class DuplicatePair(val first: Transaction, val second: Transaction) {
    /** Clave estable para recordar que el par ya se descartó. */
    val key: String = listOf(first.id, second.id).sorted().joinToString("|")
}

/**
 * Pares de cargos confirmados de la misma tarjeta con el mismo monto, fechas cercanas y comercio parecido.
 * Solo cuenta si vienen de fuentes distintas o uno es a meses y el otro de contado: dos cargos iguales de la misma
 * fuente (dos Uber de $10) son casi siempre compras reales repetidas.
 */
fun findDuplicateCandidates(transactions: List<Transaction>): List<DuplicatePair> {
    val ordered = transactions
        .filter { it.status == TxStatus.CONFIRMED && it.cardId != null }
        .sortedWith(compareBy({ it.occurredAt }, { it.id }))
    return ordered.flatMapIndexed { i, a ->
        ordered.drop(i + 1)
            .takeWhile { a.occurredAt.daysUntil(it.occurredAt) <= MAX_DAYS_APART }
            .filter { b -> isLikelyDuplicate(a, b) }
            .map { DuplicatePair(a, it) }
    }
}

private fun isLikelyDuplicate(a: Transaction, b: Transaction): Boolean =
    a.cardId == b.cardId &&
        abs(a.amount - b.amount) < AMOUNT_TOLERANCE &&
        (a.source != b.source || a.paymentMode != b.paymentMode) &&
        merchantSimilarity(a.merchant, b.merchant) >= MIN_MERCHANT_SIMILARITY

