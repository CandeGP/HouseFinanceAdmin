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
)

private const val AMOUNT_EPSILON = 0.005

// Abreviaturas que usan los estados de cuenta para el mismo comercio que muestran las notificaciones.
private val ALIASES = listOf("merpago" to "mercadopago", "mpago" to "mercadopago")

private fun merchantKey(raw: String): String {
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
    val charges = statement.movements.filter { it.amount > 0 && !it.isInstallmentCharge }

    // Gastos de contado que podrían corresponder a un cargo (con holgura alrededor del periodo).
    val pool = regular
        .filter { tx ->
            (from == null || tx.occurredAt >= from.minusDays(toleranceDays)) &&
                tx.occurredAt <= to.plusDays(toleranceDays)
        }
        .toMutableList()

    val matched = mutableListOf<StatementMatch>()
    val missing = mutableListOf<StatementMovement>()

    for (movement in charges.sortedBy { it.operationDate }) {
        val best = pool
            .filter {
                abs(it.amount - movement.amount) < AMOUNT_EPSILON &&
                    daysBetween(it.occurredAt, movement.operationDate) <= toleranceDays
            }
            .maxWithOrNull(
                compareBy<Transaction> { merchantSimilarity(it.merchant, movement.description) }
                    .thenBy { -daysBetween(it.occurredAt, movement.operationDate) },
            )
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
            .filter { abs(it.amount - installment.originalAmount) < AMOUNT_EPSILON }
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
    )
}

private fun LocalDate.minusDays(n: Int): LocalDate =
    kotlinx.datetime.LocalDate.fromEpochDays(this.toEpochDays() - n)

private fun LocalDate.plusDays(n: Int): LocalDate =
    kotlinx.datetime.LocalDate.fromEpochDays(this.toEpochDays() + n)
