package mx.finanzas.pareja.domain

import kotlin.math.abs
import mx.finanzas.pareja.data.CardCycleStatus
import mx.finanzas.pareja.data.CycleEstimate

/**
 * Lo que la app calcula que se debe en total en una tarjeta: el corte anterior sin pagar, el ciclo abierto y las
 * mensualidades que faltan. Es la cifra que se compara con el "saldo utilizado" del banco.
 */
fun debtTotal(estimate: CycleEstimate?, status: CardCycleStatus?): Double =
    status.unpaidAmount + (estimate?.cycleTotal ?: 0.0) + (estimate?.msiRemaining ?: 0.0)

/** Pesos de diferencia que se perdonan siempre (redondeos de mensualidades), o el 0.5 % del saldo si es mayor. */
const val BALANCE_TOLERANCE_MIN = 10.0
const val BALANCE_TOLERANCE_RATIO = 0.005

enum class BalanceVerdict { MATCH, BANK_HIGHER, APP_HIGHER }

data class BalanceComparison(val bank: Double, val app: Double) {
    /** Positivo: el banco dice que se debe más de lo que suma la app. */
    val diff: Double get() = bank - app

    val verdict: BalanceVerdict
        get() = when {
            abs(diff) <= maxOf(BALANCE_TOLERANCE_MIN, BALANCE_TOLERANCE_RATIO * bank) -> BalanceVerdict.MATCH
            diff > 0 -> BalanceVerdict.BANK_HIGHER
            else -> BalanceVerdict.APP_HIGHER
        }
}

fun compareBalance(app: Double, bank: Double) = BalanceComparison(bank = bank, app = app)

fun BalanceComparison.message(): String = when (verdict) {
    BalanceVerdict.MATCH -> "Cuadra con el banco (diferencia de ${formatMxn(abs(diff))})."
    BalanceVerdict.BANK_HIGHER ->
        "El banco debe ${formatMxn(diff)} más que la app: falta registrar cargos o compras a meses."
    BalanceVerdict.APP_HIGHER ->
        "La app suma ${formatMxn(-diff)} de más: puede haber un cargo duplicado o una devolución sin descontar."
}

private val ZERO_AMOUNT = Regex("""0+(\.0+)?""")

/** Saldo que teclea la persona; acepta 0 (tarjeta liquidada), que [parseAmount] rechaza como monto. */
fun parseBankBalance(text: String): Double? {
    val cleaned = text.replace("$", "").replace(",", "").filterNot { it.isWhitespace() }
    return if (ZERO_AMOUNT.matches(cleaned)) 0.0 else parseAmount(text)
}
