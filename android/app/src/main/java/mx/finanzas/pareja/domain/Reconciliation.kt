package mx.finanzas.pareja.domain

import java.text.Normalizer
import kotlin.math.abs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import mx.finanzas.pareja.data.PaymentMode
import mx.finanzas.pareja.data.Transaction

data class StatementMatch(val movement: StatementMovement, val transaction: Transaction)
data class InstallmentMatch(val installment: StatementInstallment, val transaction: Transaction)

/** Resultado de comparar un estado de cuenta contra lo registrado en la app para una tarjeta. */
data class Reconciliation(
    /** Cargos del estado que ya están registrados. */
    val matched: List<StatementMatch>,
    /** Cargos del estado que NO están en la app (candidatos a importar). */
    val missingInApp: List<StatementMovement>,
    /** Gastos de la app, dentro del periodo, que el estado no muestra (¿otro corte? ¿duplicado? ¿error?). */
    val notInStatement: List<Transaction>,
    /** Pagos y abonos del estado. */
    val payments: List<StatementMovement>,
    /** Mensualidades de compras a meses (ya representadas en la tabla de MSI). */
    val installmentCharges: List<StatementMovement>,
    val matchedInstallments: List<InstallmentMatch>,
    val missingInstallments: List<StatementInstallment>,
    /** Compras que el mismo estado devolvió: compra y devolución se cancelan y no se importan. */
    val refunded: List<RefundPair> = emptyList(),
    /** Gastos ya capturados en la app que el banco devolvió: casi seguro hay que borrarlos. */
    val refundedInApp: List<Transaction> = emptyList(),
    /** Abonos que no son pago ni devuelven una compra de este estado (compras anteriores, bonificaciones…). */
    val refundsWithoutCharge: List<StatementMovement> = emptyList(),
)

/** Una compra y la devolución que la anula dentro del mismo estado. */
data class RefundPair(val charge: StatementMovement, val refund: StatementMovement)

/** La compra puede aparecer unos días después de su devolución en el listado; no antes de este margen. */
private const val REFUND_ORDER_SLACK_DAYS = 2
private const val REFUND_MIN_SIMILARITY = 0.5

private fun pairRefunds(
    refunds: List<StatementMovement>,
    charges: List<StatementMovement>,
): Triple<List<RefundPair>, List<StatementMovement>, List<StatementMovement>> {
    val left = charges.toMutableList()
    val pairs = mutableListOf<RefundPair>()
    val orphans = mutableListOf<StatementMovement>()
    for (refund in refunds.sortedBy { it.operationDate }) {
        val charge = left
            .filter {
                abs(it.amount + refund.amount) < AMOUNT_EPSILON &&
                    it.operationDate <= refund.operationDate.plusDays(REFUND_ORDER_SLACK_DAYS) &&
                    merchantSimilarity(it.description, refund.description) >= REFUND_MIN_SIMILARITY
            }
            .maxWithOrNull(
                compareBy<StatementMovement> { merchantSimilarity(it.description, refund.description) }
                    .thenBy { -daysBetween(it.operationDate, refund.operationDate) },
            )
        if (charge != null) {
            pairs += RefundPair(charge, refund)
            left.remove(charge)
        } else {
            orphans += refund
        }
    }
    return Triple(pairs, orphans, left)
}

private fun bestMatch(pool: List<Transaction>, movement: StatementMovement, toleranceDays: Int): Transaction? =
    pool
        .filter {
            abs(it.amount - movement.amount) < AMOUNT_EPSILON &&
                daysBetween(it.occurredAt, movement.operationDate) <= toleranceDays
        }
        .maxWithOrNull(
            compareBy<Transaction> { merchantSimilarity(it.merchant, movement.description) }
                .thenBy { -daysBetween(it.occurredAt, movement.operationDate) },
        )

private const val AMOUNT_EPSILON = 0.005

// Abreviaturas que usan los estados de cuenta para el mismo comercio que muestran las notificaciones.
private val ALIASES = listOf("merpago" to "mercadopago", "mpago" to "mercadopago")

internal fun merchantKey(raw: String): String {
    val noAccents = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    var key = noAccents.lowercase().replace(Regex("[^a-z0-9]"), "")
    for ((short, long) in ALIASES) key = key.replace(short, long)
    return key
}

private fun bigrams(s: String): Map<String, Int> =
    s.windowed(2).groupingBy { it }.eachCount()

/** Similitud 0..1 (coeficiente de Dice sobre bigramas) entre dos nombres de comercio. */
fun merchantSimilarity(a: String, b: String): Double {
    val ka = merchantKey(a)
    val kb = merchantKey(b)
    if (ka.isEmpty() || kb.isEmpty()) return 0.0
    if (ka == kb) return 1.0
    if (ka.length < 2 || kb.length < 2) return 0.0
    val ba = bigrams(ka)
    val bb = bigrams(kb)
    val shared = ba.entries.sumOf { (gram, count) -> minOf(count, bb[gram] ?: 0) }
    return 2.0 * shared / (ka.length - 1 + kb.length - 1)
}

private fun daysBetween(a: LocalDate, b: LocalDate): Int = abs(a.daysUntil(b))

/**
 * Concilia un estado de cuenta con las transacciones de [cardId].
 * Empareja uno a uno por monto exacto y fecha cercana; ante varios candidatos prefiere el
 * comercio más parecido y luego la fecha más cercana.
 */
fun reconcile(
    statement: ParsedStatement,
    transactions: List<Transaction>,
    cardId: String,
    toleranceDays: Int = 5,
): Reconciliation {
    val summary = statement.summary
    val from = summary.periodStart
    val to = summary.periodEnd ?: summary.cutoffDate

    val candidates = transactions.filter { it.cardId == cardId }
    val regular = candidates.filter { it.paymentMode == PaymentMode.CONTADO }
    val msiTransactions = candidates.filter { it.paymentMode == PaymentMode.MSI }

    val payments = statement.movements.filter { it.amount < 0 }
    val installmentCharges = statement.movements.filter { it.amount > 0 && it.isInstallmentCharge }
    // Una compra devuelta en el mismo estado se cancela con su devolución: ni se importa ni cuenta como gasto.
    val refunds = statement.movements.filter { it.amount < 0 && !it.isCardPayment() }
    val (refundPairs, refundsWithoutCharge, charges) = pairRefunds(
        refunds, statement.movements.filter { it.amount > 0 && !it.isInstallmentCharge },
    )

    // Gastos de contado que podrían corresponder a un cargo (con holgura alrededor del periodo).
    val pool = regular
        .filter { tx ->
            (from == null || tx.occurredAt >= from.minusDays(toleranceDays)) &&
                tx.occurredAt <= to.plusDays(toleranceDays)
        }
        .toMutableList()

    // Si esa compra devuelta ya estaba capturada, se avisa (y no se reporta de nuevo como "no aparece").
    val refundedInApp = refundPairs.mapNotNull { pair ->
        bestMatch(pool, pair.charge, toleranceDays)?.also { pool.remove(it) }
    }

    val matched = mutableListOf<StatementMatch>()
    val missing = mutableListOf<StatementMovement>()

    for (movement in charges.sortedBy { it.operationDate }) {
        val best = bestMatch(pool, movement, toleranceDays)
        if (best != null) {
            matched += StatementMatch(movement, best)
            pool.remove(best)
        } else {
            missing += movement
        }
    }

    val inPeriod = pool.filter { tx ->
        (from == null || tx.occurredAt >= from) && tx.occurredAt <= to
    }

    // Compras a meses: por monto original; el nombre desempata.
    val msiPool = msiTransactions.toMutableList()
    val matchedInstallments = mutableListOf<InstallmentMatch>()
    val missingInstallments = mutableListOf<StatementInstallment>()
    for (installment in statement.installments) {
        val best = msiPool
            // Un centavo por mensualidad de holgura: el original reconstruido (mensualidad × meses) puede diferir del real.
            .filter { abs(it.amount - installment.originalAmount) < maxOf(AMOUNT_EPSILON, 0.01 * installment.total) }
            .maxByOrNull { merchantSimilarity(it.merchant, installment.description) }
        if (best != null) {
            matchedInstallments += InstallmentMatch(installment, best)
            msiPool.remove(best)
        } else {
            missingInstallments += installment
        }
    }

    return Reconciliation(
        matched = matched,
        missingInApp = missing,
        notInStatement = inPeriod,
        payments = payments,
        installmentCharges = installmentCharges,
        matchedInstallments = matchedInstallments,
        missingInstallments = missingInstallments,
        refunded = refundPairs,
        refundedInApp = refundedInApp,
        refundsWithoutCharge = refundsWithoutCharge,
    )
}

private fun LocalDate.minusDays(n: Int): LocalDate =
    kotlinx.datetime.LocalDate.fromEpochDays(this.toEpochDays() - n)

private fun LocalDate.plusDays(n: Int): LocalDate =
    kotlinx.datetime.LocalDate.fromEpochDays(this.toEpochDays() + n)
